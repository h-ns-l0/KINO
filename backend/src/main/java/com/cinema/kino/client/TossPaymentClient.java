package com.cinema.kino.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 토스페이먼츠 결제 API 호출을 담당하는 클라이언트
 *
 * 분리 이유:
 * - 결제 승인/취소 로직(PaymentService)과 외부 PG 통신을 분리하여
 *   승인 흐름(락, 멱등 처리, 보상 취소)을 외부 호출 없이 테스트할 수 있도록 함
 * - 테스트에서는 이 빈을 스텁으로 대체하므로 실제 토스 API를 호출하지 않음
 */
@Slf4j
@Component
public class TossPaymentClient {

    private static final String TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String TOSS_CANCEL_URL_PREFIX = "https://api.tosspayments.com/v1/payments/";

    @Value("${toss.secret-key}")
    private String secretKey;

    /**
     * 결제 승인 요청. 승인에 실패하면 RuntimeException을 던집니다.
     */
    public void confirmOrThrow(String paymentKey, String orderId, Integer amount) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = createTossHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    TOSS_CONFIRM_URL,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("PG 승인 실패 (HTTP " + resp.getStatusCode() + ")");
            }

            log.info("[토스승인성공] orderId={}, paymentKey={}", orderId, paymentKey);

        } catch (HttpStatusCodeException e) {
            // 💡 토스가 내려주는 실제 에러 메시지를 로그로 남깁니다.
            log.error("[토스 에러 응답] HTTP 상태: {}, 응답 본문: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("토스 결제 승인 실패: " + e.getResponseBodyAsString());

        } catch (RestClientException e) {
            log.error("[PG사 통신 오류] {}", e.getMessage());
            throw new RuntimeException("결제 승인 과정에서 PG사 통신 오류가 발생했습니다.");
        }
    }

    /**
     * 승인된 결제의 전액 취소 요청. 보상 처리에서 사용합니다.
     */
    public void cancelFull(String paymentKey, String reason) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = createTossHeaders();

        Map<String, Object> body = new HashMap<>();
        body.put("cancelReason", reason);

        String url = TOSS_CANCEL_URL_PREFIX + paymentKey + "/cancel";

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("PG 취소 실패 (HTTP " + resp.getStatusCode() + ")");
            }

        } catch (RestClientException e) {
            throw new RuntimeException("PG 취소 통신 오류");
        }
    }

    private HttpHeaders createTossHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String encodedAuth = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
