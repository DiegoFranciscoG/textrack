package io.github.diegofranciscog.textrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rechaza cuerpos de más de 1 MB antes de deserializarlos (OWASP API4: consumo de recursos). El lote más grande
 * válido (500 lecturas) ocupa ~200 KB. Las peticiones chunked sin Content-Length también se limitan.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    public static final long MAX_BODY_BYTES = 1_048_576;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        boolean chunked = length < 0 && "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"));
        if (length > MAX_BODY_BYTES || chunked) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"Solicitud demasiado grande\",\"status\":413}");
            return;
        }
        chain.doFilter(request, response);
    }
}
