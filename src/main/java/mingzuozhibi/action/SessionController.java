package mingzuozhibi.action;

import mingzuozhibi.persist.User;
import mingzuozhibi.support.Dao;
import mingzuozhibi.support.JsonArg;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class SessionController extends BaseController {

    @Autowired
    private Dao dao;

    @Autowired
    private UserDetailsService userDetailsService;

    @GetMapping(value = "/api/session", produces = CONTENT_TYPE)
    public String status() {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        JSONObject object = getJSON(authentication);

        LOGGER.debug("用户登入状态为: {}", object.toString());
        return objectResult(object);
    }

    private JSONObject getJSON(Authentication authentication) {
        JSONObject object = new JSONObject();
        String name = authentication.getName();
        boolean isLogged = authentication.isAuthenticated() && !"anonymousUser".equals(name);
        if (isLogged) {
            object.put("userName", name);
            object.put("isLogged", true);
            object.put("userRoles", getUserRoles(authentication));
        } else {
            object.put("userName", "Guest");
            object.put("isLogged", false);
            object.put("userRoles", new JSONArray());
        }
        return object;
    }

    private JSONArray getUserRoles(Authentication authentication) {
        JSONArray userRoles = new JSONArray();
        authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .forEach(userRoles::put);
        return userRoles;
    }

    @PostMapping(value = "/api/session", produces = CONTENT_TYPE)
    public String login(@JsonArg("$.username") String username,
                        @JsonArg("$.password") String password) {
        LOGGER.debug("用户登入: username={}, password=******", username);

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (userDetails.getPassword().equals(password) && userDetails.isEnabled()) {
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails.getUsername(), userDetails.getPassword(), userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                onLoginSuccess(username);
                LOGGER.info("用户登入: 用户已成功登入, username={}", username);
            } else {
                LOGGER.debug("用户登入: 未能成功登入, username={}", username);
            }
        } catch (UsernameNotFoundException ignored) {
            LOGGER.debug("用户登入: 未找到该用户, username={}", username);
        }

        return status();
    }

    private void onLoginSuccess(String username) {
        User user = dao.lookup(User.class, "username", username);
        user.setLastLoggedIn(LocalDateTime.now());
        dao.save(user);
    }

    @DeleteMapping(value = "/api/session", produces = CONTENT_TYPE)
    public String logout() {
        LOGGER.debug("用户登出: 正在检测登入状态");

        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        authentication.setAuthenticated(false);

        return status();
    }

}
