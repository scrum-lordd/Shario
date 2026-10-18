package com.localshare.domain.service;

import com.localshare.domain.model.Session;
import com.localshare.domain.model.User;
import com.localshare.persistence.SessionDao;
import com.localshare.persistence.UserDao;
import com.localshare.security.PasswordHasher;
import com.localshare.security.SecureTokenGenerator;
import com.localshare.server.ServerConfig;

/* JADX INFO: loaded from: classes7.dex */
public class AuthService {
    private final SessionDao sessionDao;
    private final UserDao userDao;

    public AuthService(UserDao userDao, SessionDao sessionDao) {
        this.userDao = userDao;
        this.sessionDao = sessionDao;
    }

    public User register(String username, char[] password) throws Exception {
        if (username == null || username.length() < 3 || username.length() > 32) {
            throw new IllegalArgumentException("Invalid username");
        }
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Password too short");
        }
        if (this.userDao.findByUsername(username) != null) {
            throw new IllegalStateException("Username exists");
        }
        byte[] salt = PasswordHasher.generateSalt();
        byte[] hash = PasswordHasher.hash(password, salt);
        User u = new User();
        u.id = SecureTokenGenerator.generateOpaqueId();
        u.username = username;
        u.passwordHash = PasswordHasher.encode(salt, hash);
        u.createdAt = System.currentTimeMillis();
        u.isApproved = false;
        this.userDao.insert(u);
        return u;
    }

    public String login(String username, char[] password, String ip) throws Exception {
        User u = this.userDao.findByUsername(username);
        if (u == null || !PasswordHasher.verify(password, u.passwordHash)) {
            return null;
        }
        String rawToken = SecureTokenGenerator.generateToken();
        String hash = SecureTokenGenerator.hashToken(rawToken);
        Session s = new Session();
        s.id = hash;
        s.userId = u.id;
        s.createdAt = System.currentTimeMillis();
        s.expiresAt = s.createdAt + ServerConfig.SESSION_TTL_MS;
        s.lastSeen = s.createdAt;
        s.ip = ip;
        this.sessionDao.insert(s);
        return rawToken;
    }

    public Session validateSession(String rawToken) {
        String hash;
        Session s;
        if (rawToken == null || (s = this.sessionDao.findById((hash = SecureTokenGenerator.hashToken(rawToken)))) == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now > s.expiresAt) {
            this.sessionDao.delete(hash);
            return null;
        }
        long j = s.lastSeen;
        this.sessionDao.updateLastSeen(hash, now);
        s.lastSeen = now;
        return s;
    }

    public void logout(String rawToken) {
        if (rawToken == null) {
            return;
        }
        this.sessionDao.delete(SecureTokenGenerator.hashToken(rawToken));
    }

    public User getUserForSession(Session s) {
        return this.userDao.findById(s.userId);
    }
}
