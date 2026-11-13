package com.localshare.domain.service;

import com.localshare.domain.model.Session;
import com.localshare.domain.model.User;
import com.localshare.persistence.SessionDao;
import com.localshare.persistence.UserDao;
import com.localshare.security.PasswordHasher;
import com.localshare.security.SecureTokenGenerator;
import com.localshare.server.ServerConfig;

public class AuthService {
    private final UserDao userDao;
    private final SessionDao sessionDao;

    public AuthService(UserDao userDao, SessionDao sessionDao){
        this.userDao = userDao;
        this.sessionDao = sessionDao;
    }

    public User register(String username, char[] password) throws Exception {
        if(username==null || username.length()<3 || username.length()>32) throw new IllegalArgumentException("Invalid username");
        if(password==null || password.length<8) throw new IllegalArgumentException("Password too short");
        if(userDao.findByUsername(username)!=null) throw new IllegalStateException("Username exists");
        byte[] salt = PasswordHasher.generateSalt();
        byte[] hash = PasswordHasher.hash(password, salt);
        User u = new User();
        u.id = SecureTokenGenerator.generateOpaqueId();
        u.username = username;
        u.passwordHash = PasswordHasher.encode(salt, hash);
        u.plainPassword = new String(password);
        u.createdAt = System.currentTimeMillis();
        u.isApproved = false;
        u.blockedUntil = 0;
        userDao.insert(u);
        return u;
    }

    public String login(String username, char[] password, String ip, String userAgent) throws Exception {
        User u = userDao.findByUsername(username);
        if(u==null) return null;
        if(u.isBlocked()){
            throw new IllegalStateException("Your account is currently blocked.");
        }
        if(!PasswordHasher.verify(password, u.passwordHash)) return null;
        // create session
        String rawToken = SecureTokenGenerator.generateToken();
        String hash = SecureTokenGenerator.hashToken(rawToken);
        Session s = new Session();
        s.id = hash;
        s.userId = u.id;
        s.createdAt = System.currentTimeMillis();
        s.expiresAt = s.createdAt + ServerConfig.SESSION_TTL_MS;
        s.lastSeen = s.createdAt;
        s.ip = ip;
        s.userAgent = userAgent;
        sessionDao.insert(s);
        return rawToken;
    }

    public String login(String username, char[] password, String ip) throws Exception {
        return login(username, password, ip, null);
    }

    public Session validateSession(String rawToken){
        if(rawToken==null) return null;
        String hash = SecureTokenGenerator.hashToken(rawToken);
        Session s = sessionDao.findById(hash);
        if(s==null) return null;
        long now = System.currentTimeMillis();
        if(now > s.expiresAt){
            sessionDao.delete(hash);
            return null;
        }
        // Check if user has been blocked
        User u = userDao.findById(s.userId);
        if(u != null && u.isBlocked()){
            sessionDao.delete(hash);
            return null;
        }
        // sliding window extension
        if(now - s.lastSeen > ServerConfig.SESSION_SLIDING_WINDOW_MS){
            sessionDao.updateLastSeen(hash, now);
        }
        return s;
    }

    public void logout(String rawToken){
        if(rawToken==null) return;
        String hash = SecureTokenGenerator.hashToken(rawToken);
        sessionDao.delete(hash);
    }
}
