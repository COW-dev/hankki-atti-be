package com.hankkiatti.global.config;

import com.hankkiatti.global.security.AuthProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Slf4j
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(AuthProperties authProperties) throws NoSuchAlgorithmException {
        String secret = authProperties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            // 재시작하면 access 토큰만 무효가 된다. refresh 토큰은 DB에 있어 프론트가 refresh로 새 토큰을 받는다.
            // 서버를 2대 이상 띄우면 키가 서로 달라지므로 그때는 secret을 지정해야 한다
            log.warn("auth.jwt.secret이 없어 임의 키로 JWT를 서명합니다. 재시작하면 기존 access 토큰은 무효가 됩니다(서버 2대 이상이면 secret 지정 필요).");
            return KeyGenerator.getInstance(HMAC_ALGORITHM).generateKey();
        }

        byte[] keyBytes = Base64.getDecoder().decode(secret.trim());
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("auth.jwt.secret은 Base64로 인코딩된 32바이트 이상이어야 합니다.");
        }
        return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AuthProperties authProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(authProperties.jwt().issuer()));
        return decoder;
    }
}
