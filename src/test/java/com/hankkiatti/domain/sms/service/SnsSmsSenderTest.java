package com.hankkiatti.domain.sms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

@ExtendWith(MockitoExtension.class)
class SnsSmsSenderTest {

    @Mock
    private ObjectProvider<SnsClient> snsClientProvider;

    @Mock
    private SnsClient snsClient;

    @Test
    void send_수신번호와본문을Transactional문자로발행() {
        // given
        given(snsClientProvider.getObject()).willReturn(snsClient);
        given(snsClient.publish(any(PublishRequest.class))).willReturn(PublishResponse.builder().messageId("m-1").build());
        SnsSmsSender sender = new SnsSmsSender(snsClientProvider);

        // when
        sender.send("+821012345678", "[한끼아띠] 도우미가 정해졌어요");

        // then
        ArgumentCaptor<PublishRequest> request = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(request.capture());
        assertThat(request.getValue().phoneNumber()).isEqualTo("+821012345678");
        assertThat(request.getValue().message()).isEqualTo("[한끼아띠] 도우미가 정해졌어요");
        assertThat(request.getValue().messageAttributes().get(SnsSmsSender.SMS_TYPE_ATTRIBUTE).stringValue())
                .isEqualTo(SnsSmsSender.TRANSACTIONAL);
    }

    @Test
    void send_SDK예외_SmsSendException으로바꿈() {
        // given
        given(snsClientProvider.getObject()).willReturn(snsClient);
        given(snsClient.publish(any(PublishRequest.class)))
                .willThrow(SnsException.builder().message("Rate exceeded").build());
        SnsSmsSender sender = new SnsSmsSender(snsClientProvider);

        // when & then
        assertThatThrownBy(() -> sender.send("+821012345678", "본문"))
                .isInstanceOf(SmsSendException.class)
                .hasMessageContaining("Rate exceeded");
    }

    @Test
    void isConfigured_클라이언트빈이없으면_미설정() {
        // given
        given(snsClientProvider.getIfAvailable()).willReturn(null);

        // when & then
        assertThat(new SnsSmsSender(snsClientProvider).isConfigured()).isFalse();
    }
}
