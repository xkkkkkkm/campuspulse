package com.campuspulse.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String id=req.getHeader("X-Request-ID");
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,64}")) id=UUID.randomUUID().toString();
        res.setHeader("X-Request-ID",id);
        res.setHeader("X-Content-Type-Options","nosniff");
        res.setHeader("X-Frame-Options","DENY");
        res.setHeader("Referrer-Policy","same-origin");
        MDC.put("requestId",id);
        try { chain.doFilter(req,res); } finally { MDC.remove("requestId"); }
    }
}
