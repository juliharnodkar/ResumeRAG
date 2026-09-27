package com.example.resumerag;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HtmlUtf8ResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, new Utf8HtmlResponse(response));
    }

    private static final class Utf8HtmlResponse extends HttpServletResponseWrapper {
        private Utf8HtmlResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setContentType(String type) {
            super.setContentType(withUtf8(type));
        }

        @Override
        public void setHeader(String name, String value) {
            if ("Content-Type".equalsIgnoreCase(name)) {
                super.setHeader(name, withUtf8(value));
                return;
            }
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if ("Content-Type".equalsIgnoreCase(name)) {
                super.addHeader(name, withUtf8(value));
                return;
            }
            super.addHeader(name, value);
        }

        private static String withUtf8(String type) {
            if (type == null || type.toLowerCase().contains("charset=")) {
                return type;
            }
            if (type.startsWith("text/html") || type.startsWith("text/plain")
                    || type.startsWith("application/json") || type.startsWith("text/javascript")) {
                return type + ";charset=" + StandardCharsets.UTF_8.name();
            }
            return type;
        }
    }
}
