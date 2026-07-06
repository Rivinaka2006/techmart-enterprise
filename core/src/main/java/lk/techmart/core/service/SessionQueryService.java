package lk.techmart.core.service;

import lk.techmart.core.entity.UserSession;

import java.util.List;
import java.util.Map;

public interface SessionQueryService {
    Map<String, Long> getSessionCounts();
    List<UserSession> getActiveSessions();
    List<UserSession> getAllSessions(int offset, int limit);
    Map<String, Object> getSessionDashboardData(String status, int offset, int limit);
    Integer startSession(Integer userId);
    void endSession(Integer sessionId);
    void expireIdleSessions();
}
