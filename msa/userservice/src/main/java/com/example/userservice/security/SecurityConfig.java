package com.example.userservice.security;

import com.example.userservice.domain.vo.JsonAuthenticationToken;
import com.example.userservice.domain.vo.MemberContext;
import com.example.userservice.domain.vo.UserDetailsVo;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.UserDetailsServiceImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;

    private final UserRepository userRepository;

    private final Environment env;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable();
        http.headers().frameOptions().disable();

        http.sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS); // ?????? ?????? ?????? ??????

        http.authorizeHttpRequests()
                .antMatchers(HttpMethod.GET, "/users").authenticated()
                .anyRequest().permitAll();
        http
                .addFilterBefore(new JwtAuthenticationFilter(env), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(getAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new UserDetailsServiceImpl(userRepository);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return new JsonAuthenticationProvider(userDetailsService(), passwordEncoder());
    }

    private AuthenticationFilter getAuthenticationFilter() throws Exception {
        AuthenticationFilter authenticationFilter = new AuthenticationFilter();
        authenticationFilter.setAuthenticationManager(authenticationManager(authenticationConfiguration));
        authenticationFilter.setAuthenticationSuccessHandler((request, response, authentication) -> {
            response.setStatus(HttpServletResponse.SC_OK);
            long expiration = Long.parseLong(env.getProperty("token.expiration-time"));

            // ?????? ??????????????? ?????? ??? ??? ??????
            SecretKey key = Keys.hmacShaKeyFor(env.getProperty("token.secret").getBytes(StandardCharsets.UTF_8));

            JsonAuthenticationToken jsonAuthenticationToken = (JsonAuthenticationToken) authentication;
            MemberContext memberContext = jsonAuthenticationToken.getMemberContext();

            UserDetailsVo userDetailsVo = UserDetailsVo.builder()
                    .email(memberContext.getEmail())
                    .name(memberContext.getName())
                    .authorities(memberContext.getAuthorities())
                    .username(memberContext.getUsername())
                    .build();


            // jwt ?????? ??????
            String jwt = Jwts.builder()
                    .setHeaderParam("typ", "JWT") // ????????? typ?????? ?????? ?????? ??????
                    .claim("sub", userDetailsVo) // claim??? sub?????? ?????? ?????? ??????
//                    .setSubject((String) authentication.getPrincipal()) // ?????? ?????? claim??? sub?????? ?????? ?????? ??????
                    .setExpiration(new Date(System.currentTimeMillis() + expiration)) // ?????? ?????? ??????
                    .signWith(key, SignatureAlgorithm.HS512) // ?????? ????????????
                    .compact();

            response.addHeader("JWT", jwt);
        });
        authenticationFilter.setAuthenticationFailureHandler((request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        });
        return authenticationFilter;
    }
}
