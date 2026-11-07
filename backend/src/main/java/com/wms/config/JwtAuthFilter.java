package com.wms.config;

import com.wms.entity.User;
import com.wms.repository.UserRepository;
import com.wms.repository.RoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository, RoleRepository roleRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.validateToken(token)) {
                try {
                    String username = jwtUtil.getUsernameFromToken(token);
                    User user = userRepository.findByUsername(username).orElse(null);
                    if (user != null && user.isEnabled()) {
                        String role = user.getRole();
                        var roleEntity=roleRepository.findByCode(role).orElse(null);
                        if(roleEntity==null||!roleEntity.isEnabled()){filterChain.doFilter(request,response);return;}
                        java.util.List<SimpleGrantedAuthority> authorities=new java.util.ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_"+role));
                        roleEntity.getPermissions().forEach(p->authorities.add(new SimpleGrantedAuthority(p)));
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                authorities
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (Exception ignored) {
                    // 无效 token：不设置 authentication
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
