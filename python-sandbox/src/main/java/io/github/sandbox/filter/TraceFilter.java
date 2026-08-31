package io.github.sandbox.filter;

import io.github.sandbox.util.TraceUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求追踪过滤器
 * 在每个请求入口处生成或获取 traceId，并设置到 ThreadLocal 中
 */
@Slf4j
@Component
@Order(1)
@WebFilter(urlPatterns = "/*")
public class TraceFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // 在请求处理前初始化 traceId
            String traceId = TraceUtil.getOrGenerateTraceId(httpRequest);
            
            // 将 traceId 设置到响应头，方便客户端追踪
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            
            // 继续处理请求
            chain.doFilter(request, response);
        } finally {
            // 请求完成后清理 ThreadLocal，避免内存泄漏
            TraceUtil.clearTraceId();
        }
    }
}
