package com.calm.cas.client.cluster;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.StandardSessionFacade;
import org.jasig.cas.client.session.SessionMappingStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.lang.reflect.Field;

/**
* cas session 映射
* Created by dingqihui on 2016/6/2.
*/
public class RedisSessionMappingStorage implements SessionMappingStorage {
    private Logger log= LoggerFactory.getLogger(RedisSessionMappingStorage.class);
    private RedisTemplate<String,String> token2sessionRedisTemplate;
    private RedisTemplate<String,String> session2TokenRedisTemplate;
    private String applicationName;
    private Manager manager;
    @Override
    public HttpSession removeSessionByMappingId(String mappingId) {
        String STKey = getKey(mappingId);
        log.debug("cas-client remove session, STKey:" + STKey);
        String sessionId =  token2sessionRedisTemplate.opsForValue().get(STKey);
        if (null == sessionId) {
            log.error("session is null");
            return null;
        }

        removeBySessionById(sessionId);
        token2sessionRedisTemplate.delete(sessionId);
        log.debug("delete session:" + sessionId);

        try{
            return (HttpSession)manager.findSession(sessionId);
        }catch (IOException e){
            throw new RuntimeException(e);
        }

    }

    @Override
    public void removeBySessionById(String sessionId) {
        log.debug("Attempting to remove Session=[" + sessionId + "]");
        String sessionKey = getKey(sessionId);
        String st = session2TokenRedisTemplate.opsForValue().get(sessionKey);

        if (log.isDebugEnabled()) {
            if (st != null) {
                log.debug("Found mapping for session.  Session Removed.");
            } else {
                log.debug("No mapping for session found.  Ignoring.");
            }
        }
        session2TokenRedisTemplate.delete(sessionKey);
        if(st!=null){
            token2sessionRedisTemplate.delete(st);
        }
    }

    @Override
    public void addSessionById(String mappingId, HttpSession session) {
        String STKey = getKey(mappingId);
        if(manager==null) {
            StandardSessionFacade standardSessionFacade = (StandardSessionFacade) session;

            StandardSession redisSession = null;
            try {
                redisSession = (StandardSession) getValue(standardSessionFacade, "session");
                manager = redisSession.getManager();
            } catch (IllegalAccessException | NoSuchFieldException e) {
                log.error(e.getMessage(),e);
            }
        }
//        if (null == redisSession) {
//            log.error("get redisSession fail");
//            return;
//        }

        token2sessionRedisTemplate.opsForValue().set(STKey, session.getId());
        String sessionKey = getKey(session.getId());
        session2TokenRedisTemplate.opsForValue().set(sessionKey, STKey);
        log.debug("cas-client add session, mappingId:" + mappingId + " sessionId:" + session.getId());
    }
    private String getKey(String key){
        if(applicationName!=null){
            return applicationName+"/"+key;
        }
        return key;

    }
    private HttpSession getValue(StandardSessionFacade standardSessionFacade,String properties)throws IllegalAccessException,NoSuchFieldException{
        Class<?> clazz=standardSessionFacade.getClass();
        Field field=clazz.getDeclaredField(properties);
        field.setAccessible(true);
        return (HttpSession) field.get(standardSessionFacade);
    }

    public void setSession2TokenRedisTemplate(RedisTemplate<String, String> session2TokenRedisTemplate) {
        this.session2TokenRedisTemplate = session2TokenRedisTemplate;
    }

    public void setToken2sessionRedisTemplate(RedisTemplate<String, String> token2sessionRedisTemplate) {
        this.token2sessionRedisTemplate = token2sessionRedisTemplate;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}
