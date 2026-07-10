package com.sion.pos.application.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * 매장 계정의 세션을 principal(loginId) 색인으로 조회·무효화한다(모든 기기 로그아웃).
 * cluster 프로파일 전용 — 세션이 Redis 에 공유·색인돼 있어야 한 계정의 전 세션을 열거해 지울 수 있다.
 */
@Service
@Profile("cluster")
public class AccountSessionService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public AccountSessionService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** 해당 계정의 모든 세션을 무효화하고, 무효화한 세션 수를 반환한다. */
    public int logoutAllDevices(String loginId) {
        var sessions = sessionRepository.findByPrincipalName(loginId);
        sessions.keySet().forEach(sessionRepository::deleteById);
        return sessions.size();
    }
}