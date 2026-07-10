package com.sion.pos.interfaces.api.auth;

import com.sion.pos.application.auth.AccountSessionService;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 관리 API. 멀티인스턴스(cluster 프로파일)에서만 노출된다 —
 * 서버측 세션 강제 무효화는 Redis 공유 세션 + principal 색인이 있어야 성립하기 때문.
 */
@RestController
@Profile("cluster")
@RequestMapping("/api/v1/auth")
public class AuthSessionApiController {

    private final AccountSessionService accountSessionService;

    public AuthSessionApiController(AccountSessionService accountSessionService) {
        this.accountSessionService = accountSessionService;
    }

    /** 로그인한 본인 계정의 모든 기기 세션을 무효화한다(자신의 현재 세션 포함). */
    @PostMapping("/logout-all-devices")
    public ResponseEntity<Void> logoutAllDevices(Principal principal) {
        accountSessionService.logoutAllDevices(principal.getName());
        return ResponseEntity.noContent().build();
    }
}