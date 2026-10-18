package com.cinema.kino.service;

import com.cinema.kino.client.TossPaymentClient;
import com.cinema.kino.dto.PaymentDTO;
import com.cinema.kino.entity.*;
import com.cinema.kino.entity.enums.*;
import com.cinema.kino.repository.PaymentRepository;
import com.cinema.kino.repository.ScreeningSeatRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 결제 승인(confirmPayment)의 동시성 제어와 보상 처리를 검증한다.
 *
 * 검증 대상:
 * - 비관적 락(PESSIMISTIC_WRITE) + PAID 상태 검사로 중복 승인을 막는지
 * - PG 승인 후 내부 처리가 실패하면 전액 취소 보상 처리가 호출되는지
 * - 클라이언트가 변조한 금액을 PG 호출 전에 거부하는지
 * - merchant_uid UNIQUE 제약이 두 번째 방어선으로 동작하는지
 *
 * 격리 원칙:
 * - 외부 PG(TossPaymentClient)와 메일 발송(MailService)은 스텁으로 대체하므로
 *   실제 토스페이먼츠 API나 SMTP를 호출하지 않는다.
 * - DB는 인메모리 H2(MODE=MySQL)를 사용한다. H2도 SELECT ... FOR UPDATE를 지원하여
 *   동일 레코드에 대한 두 번째 트랜잭션이 대기하도록 재현된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("결제 승인 동시성·보상 처리")
class PaymentConfirmTest {

    private static final String PAYMENT_KEY = "test_payment_key";
    private static final int AMOUNT = 14_000;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    /** 외부 PG 호출 격리용 스텁 */
    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    /** 실제 SMTP 발송 방지용 스텁 */
    @MockitoBean
    private MailService mailService;

    /** 승인 후 내부 처리 실패를 주입하기 위한 스파이 */
    @MockitoSpyBean
    private ScreeningSeatRepository screeningSeatRepository;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("동일 주문번호로 동시에 승인 요청해도 PG 승인은 1회만 수행된다")
    void concurrentConfirm_callsPgOnlyOnce() throws Exception {
        ReadyPayment ready = createReadyPayment();

        // PG 승인에 지연을 주어, 락이 없다면 두 요청이 모두 PAID 검사를 통과하는 구간을 넓힌다.
        AtomicInteger pgConfirmCalls = new AtomicInteger();
        doAnswer(invocation -> {
            pgConfirmCalls.incrementAndGet();
            Thread.sleep(300);
            return null;
        }).when(tossPaymentClient).confirmOrThrow(anyString(), anyString(), anyInt());

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentDTO.ConfirmResponse>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return paymentService.confirmPayment(confirmRequest(ready.orderId(), AMOUNT));
                }));
            }
            start.countDown();

            List<Long> returnedPaymentIds = new ArrayList<>();
            for (Future<PaymentDTO.ConfirmResponse> future : futures) {
                returnedPaymentIds.add(future.get(30, TimeUnit.SECONDS).getPaymentId());
            }

            // 1) PG 승인 API는 한 번만 호출된다.
            assertThat(pgConfirmCalls.get()).isEqualTo(1);

            // 2) 두 요청 모두 동일한 결제 건의 결과를 받는다(두 번째는 기존 결과 반환).
            assertThat(returnedPaymentIds).containsOnly(ready.paymentId());
        } finally {
            pool.shutdownNow();
        }

        // 3) 결제 레코드는 PAID 1건으로 확정된다.
        Payment confirmed = paymentRepository.findByMerchantUid(ready.orderId()).orElseThrow();
        assertThat(confirmed.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(confirmed.getImpUid()).isEqualTo(PAYMENT_KEY);
        assertThat(paymentRepository.findAll().stream()
                .filter(p -> ready.orderId().equals(p.getMerchantUid()))
                .filter(p -> p.getPaymentStatus() == PaymentStatus.PAID)
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("PG 승인 후 내부 처리가 실패하면 전액 취소를 호출하고 결제 확정은 롤백된다")
    void internalFailureAfterPgApproval_triggersFullCancel() {
        ReadyPayment ready = createReadyPayment();

        // 승인 직후의 좌석 상태 갱신 단계에서 실패를 주입한다.
        doThrow(new RuntimeException("좌석 상태 갱신 실패"))
                .when(screeningSeatRepository).findByReservationId(ready.reservationId());

        assertThatThrownBy(() -> paymentService.confirmPayment(confirmRequest(ready.orderId(), AMOUNT)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("좌석 상태 갱신 실패");

        // 1) PG 승인은 성공했고, 보상용 전액 취소가 호출된다.
        verify(tossPaymentClient).confirmOrThrow(PAYMENT_KEY, ready.orderId(), AMOUNT);
        verify(tossPaymentClient).cancelFull(eq(PAYMENT_KEY), anyString());

        // 2) 내부 확정 처리는 트랜잭션 롤백으로 되돌려진다.
        Payment rolledBack = paymentRepository.findByMerchantUid(ready.orderId()).orElseThrow();
        assertThat(rolledBack.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(rolledBack.getPaidAt()).isNull();
        assertThat(rolledBack.getImpUid()).isNull();
    }

    @Test
    @DisplayName("변조된 금액으로 승인 요청하면 PG 호출 없이 거부된다")
    void tamperedAmount_isRejectedBeforePgCall() {
        ReadyPayment ready = createReadyPayment();

        assertThatThrownBy(() -> paymentService.confirmPayment(confirmRequest(ready.orderId(), AMOUNT - 1_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("금액 불일치");

        // 1) PG 승인·취소 모두 호출되지 않는다.
        verifyNoInteractions(tossPaymentClient);

        // 2) 결제는 확정되지 않는다.
        //    단, FAILED 기록도 같은 트랜잭션에서 롤백되므로 DB 상태는 READY로 남는다(한계).
        Payment rejected = paymentRepository.findByMerchantUid(ready.orderId()).orElseThrow();
        assertThat(rejected.getPaymentStatus()).isNotEqualTo(PaymentStatus.PAID);
        assertThat(rejected.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("같은 merchantUid로 결제 레코드를 추가 저장하면 DB UNIQUE 제약이 거부한다")
    void duplicateMerchantUid_isRejectedByUniqueConstraint() {
        ReadyPayment first = createReadyPayment();
        ReadyPayment second = createReadyPayment();

        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            Reservation otherReservation = em.find(Reservation.class, second.reservationId());
            Payment duplicate = Payment.builder()
                    .reservation(otherReservation)
                    .merchantUid(first.orderId()) // 이미 사용된 주문번호
                    .originalAmount(AMOUNT)
                    .discount(0)
                    .usedPoints(0)
                    .finalAmount(AMOUNT)
                    .paymentMethod("CARD")
                    .paymentStatus(PaymentStatus.READY)
                    .build();
            paymentRepository.saveAndFlush(duplicate);
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------------
    // 테스트 픽스처
    // ---------------------------------------------------------------------

    /** 승인 대기(READY) 상태의 결제 1건과 연결된 예매·좌석 정보 */
    private record ReadyPayment(Long paymentId, Long reservationId, String orderId) {
    }

    /**
     * 비회원(게스트) 예매 기준으로 READY 상태의 결제를 하나 만들어 커밋한다.
     * 별도 트랜잭션에서 커밋해야 동시 승인 테스트의 두 스레드가 같은 레코드를 조회할 수 있다.
     */
    private ReadyPayment createReadyPayment() {
        return tx().execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String orderId = "ORD-" + suffix;

            Region region = Region.builder().name("테스트지역-" + suffix).build();
            em.persist(region);

            Theater theater = Theater.builder()
                    .region(region)
                    .name("테스트극장-" + suffix)
                    .address("서울시 테스트구 테스트로 1")
                    .build();
            em.persist(theater);

            Screen screen = Screen.builder()
                    .theater(theater)
                    .screenType(ScreenType._2D)
                    .totalSeats(1)
                    .name("1관")
                    .build();
            em.persist(screen);

            Movie movie = Movie.builder()
                    .title("테스트영화-" + suffix)
                    .ageRating(Rating.ALL)
                    .releaseDate(LocalDate.now())
                    .durationMin(120)
                    .build();
            em.persist(movie);

            Screening screening = Screening.builder()
                    .screen(screen)
                    .movie(movie)
                    .startTime(LocalDateTime.now().plusHours(3))
                    .endTime(LocalDateTime.now().plusHours(5))
                    .build();
            em.persist(screening);

            Seat seat = Seat.builder()
                    .screen(screen)
                    .seatRow("A")
                    .seatNumber(1)
                    .seatType(SeatType.NORMAL)
                    .build();
            em.persist(seat);

            Guest guest = Guest.builder()
                    .name("테스터-" + suffix)
                    .tel("010-0000-" + suffix.substring(0, 4))
                    .password("hashed-password")
                    .uuid(UUID.randomUUID().toString())
                    .build();
            em.persist(guest);

            Reservation reservation = Reservation.builder()
                    .guest(guest)
                    .screening(screening)
                    .totalPrice(AMOUNT)
                    .totalNum(1)
                    .status(ReservationStatus.PENDING)
                    .orderId(orderId)
                    .build();
            em.persist(reservation);

            ScreeningSeat screeningSeat = ScreeningSeat.builder()
                    .screening(screening)
                    .seat(seat)
                    .reservation(reservation)
                    .status(SeatStatus.HELD)
                    .priceType(PriceType.ADULT)
                    .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
                    .build();
            em.persist(screeningSeat);

            Payment payment = Payment.builder()
                    .reservation(reservation)
                    .guest(guest)
                    .merchantUid(orderId)
                    .originalAmount(AMOUNT)
                    .discount(0)
                    .usedPoints(0)
                    .finalAmount(AMOUNT)
                    .paymentMethod("CARD")
                    .paymentStatus(PaymentStatus.READY)
                    .build();
            em.persist(payment);

            em.flush();
            return new ReadyPayment(payment.getId(), reservation.getId(), orderId);
        });
    }

    private PaymentDTO.ConfirmRequest confirmRequest(String orderId, int amount) {
        return PaymentDTO.ConfirmRequest.builder()
                .paymentKey(PAYMENT_KEY)
                .orderId(orderId)
                .amount(amount)
                .build();
    }
}
