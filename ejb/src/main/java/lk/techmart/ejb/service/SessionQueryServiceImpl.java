package lk.techmart.ejb.service;

import jakarta.ejb.Schedule;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lk.techmart.core.entity.UserSession;
import lk.techmart.core.service.SessionQueryService;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Stateless
public class SessionQueryServiceImpl implements SessionQueryService {

    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @Override
    public Map<String, Long> getSessionCounts() {
        Map<String, Long> counts = new HashMap<>();

        counts.put("active", em.createQuery(
            "SELECT COUNT(s) FROM UserSession s WHERE s.status = 'ACTIVE'", Long.class
        ).getSingleResult());

        counts.put("expired", em.createQuery(
            "SELECT COUNT(s) FROM UserSession s WHERE s.status = 'EXPIRED'", Long.class
        ).getSingleResult());

        counts.put("idle", em.createQuery(
            "SELECT COUNT(s) FROM UserSession s WHERE s.status = 'IDLE'", Long.class
        ).getSingleResult());

        return counts;
    }

    @Override
    public List<UserSession> getActiveSessions() {
        TypedQuery<UserSession> query = em.createQuery(
            "SELECT s FROM UserSession s WHERE s.status = 'ACTIVE' ORDER BY s.loginTime DESC",
            UserSession.class
        );
        return query.getResultList();
    }

    @Override
    public List<UserSession> getAllSessions(int offset, int limit) {
        TypedQuery<UserSession> query = em.createQuery(
            "SELECT s FROM UserSession s ORDER BY s.loginTime DESC",
            UserSession.class
        );
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    @Override
    public Map<String, Object> getSessionDashboardData(String status, int offset, int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Long> counts = getSessionCounts();
        Long total = em.createQuery("SELECT COUNT(s) FROM UserSession s", Long.class)
                .getSingleResult();

        List<UserSession> sessions = findSessions(status, offset, limit);

        data.put("counts", counts);
        data.put("totalSessions", total);
        data.put("avgDurationSeconds", getAverageDurationSeconds());
        data.put("sessions", toSessionRows(sessions));
        return data;
    }

    @Override
    public Integer startSession(Integer userId) {
        UserSession session = new UserSession();
        session.setUser(em.getReference(lk.techmart.core.entity.User.class, userId));
        session.setLoginTime(LocalDateTime.now());
        session.setStatus("ACTIVE");
        em.persist(session);
        em.flush();
        return session.getId();
    }

    @Override
    public void endSession(Integer sessionId) {
        if (sessionId == null) return;

        UserSession session = em.find(UserSession.class, sessionId);
        if (session == null) return;

        session.setStatus("EXPIRED");
        session.setLogoutTime(LocalDateTime.now());
    }

    private List<UserSession> findSessions(String status, int offset, int limit) {
        String jpql = "SELECT s FROM UserSession s LEFT JOIN FETCH s.user ";
        if (status != null && !status.isBlank()) {
            jpql += "WHERE s.status = :status ";
        }
        jpql += "ORDER BY s.loginTime DESC";

        TypedQuery<UserSession> query = em.createQuery(jpql, UserSession.class);
        if (status != null && !status.isBlank()) {
            query.setParameter("status", status.toUpperCase());
        }
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private List<Map<String, Object>> toSessionRows(List<UserSession> sessions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (UserSession session : sessions) {
            Map<String, Object> row = new LinkedHashMap<>();
            LocalDateTime end = session.getLogoutTime() == null ? now : session.getLogoutTime();

            row.put("id", session.getId());
            row.put("username", session.getUser() == null ? "Unknown" : session.getUser().getUsername());
            row.put("email", session.getUser() == null ? "" : session.getUser().getEmail());
            row.put("loginTime", session.getLoginTime() == null ? null : session.getLoginTime().toString());
            row.put("logoutTime", session.getLogoutTime() == null ? null : session.getLogoutTime().toString());
            row.put("lastSeenAt", end.toString());
            row.put("status", session.getStatus());
            row.put("durationSeconds", calculateDurationSeconds(session.getLoginTime(), end));
            rows.add(row);
        }
        return rows;
    }

    private long getAverageDurationSeconds() {
        List<UserSession> sessions = em.createQuery(
                "SELECT s FROM UserSession s WHERE s.loginTime IS NOT NULL",
                UserSession.class
        ).setMaxResults(500).getResultList();

        if (sessions.isEmpty()) return 0L;

        LocalDateTime now = LocalDateTime.now();
        long totalSeconds = 0L;
        for (UserSession session : sessions) {
            totalSeconds += calculateDurationSeconds(
                    session.getLoginTime(),
                    session.getLogoutTime() == null ? now : session.getLogoutTime()
            );
        }
        return totalSeconds / sessions.size();
    }

    private long calculateDurationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) return 0L;
        return Duration.between(start, end).getSeconds();
    }

    private static final int DEFAULT_TIMEOUT_MINUTES = 30;

    @Override
    @Schedule(hour = "*", minute = "*/5", persistent = false)
    public void expireIdleSessions() {
        Instant nowInstant = Instant.now();
        LocalDateTime now = LocalDateTime.ofInstant(nowInstant, ZoneId.systemDefault());
        LocalDateTime cutoff = now.minusMinutes(DEFAULT_TIMEOUT_MINUTES);

        int expired = em.createQuery(
            "UPDATE UserSession s SET s.status = 'EXPIRED', s.logoutTime = :now " +
            "WHERE s.status = 'ACTIVE' AND s.loginTime < :cutoff"
        )
        .setParameter("now", now)
        .setParameter("cutoff", cutoff)
        .executeUpdate();

        if (expired > 0) {
            log.info("Expired {} idle sessions older than {} minutes", expired, DEFAULT_TIMEOUT_MINUTES);
        }
    }
}
