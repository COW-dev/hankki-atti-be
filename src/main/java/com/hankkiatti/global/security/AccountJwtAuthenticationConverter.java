package com.hankkiatti.global.security;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * 서명·만료가 검증된 JWT를 인증 정보로 바꾼다.
 * 요청마다 계정을 조회해 활성 상태·tokenVersion·역할을 확인한다 — 비밀번호 변경이나 비활성화가 access 토큰 만료를 기다리지 않고 바로 반영된다.
 */
@Component
@RequiredArgsConstructor
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AccountRepository accountRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long accountId = parseAccountId(jwt.getSubject());
        TokenAudience audience = parseAudience(jwt.getAudience());
        Number version = jwt.getClaim(JwtTokenProvider.VERSION_CLAIM);
        if (accountId == null || audience == null || version == null) {
            throw invalidToken();
        }

        Account account = accountRepository.findById(accountId).orElseThrow(this::invalidToken);
        if (!account.isActive()
                || account.getTokenVersion() != version.intValue()
                || !audience.allows(account.getRole())) {
            throw invalidToken();
        }

        AuthPrincipal principal = new AuthPrincipal(accountId, account.getRole(), audience);
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, List.of(authorityOf(account)));
    }

    private SimpleGrantedAuthority authorityOf(Account account) {
        // 관리자는 비밀번호 변경 화면이 없어 변경 필요 상태를 적용하지 않는다
        if (account.getRole() != AccountRole.ADMIN && account.isMustChangePassword()) {
            return new SimpleGrantedAuthority(AuthAuthorities.PASSWORD_CHANGE_ONLY);
        }
        return new SimpleGrantedAuthority(AuthAuthorities.role(account.getRole()));
    }

    private Long parseAccountId(String subject) {
        if (subject == null) {
            return null;
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private TokenAudience parseAudience(List<String> audiences) {
        if (audiences == null || audiences.size() != 1) {
            return null;
        }
        return TokenAudience.fromClaim(audiences.get(0));
    }

    private InvalidBearerTokenException invalidToken() {
        return new InvalidBearerTokenException("유효하지 않은 토큰입니다.");
    }
}
