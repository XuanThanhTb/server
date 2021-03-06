package org.ititandev.security;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ititandev.Application;
import org.ititandev.config.Config;
import org.ititandev.dao.UserDAO;
import org.ititandev.service.MailService;
import org.ititandev.service.TokenAuthenticationService;
import org.ititandev.service.TokenAuthenticationServiceImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JWTLoginFilter extends AbstractAuthenticationProcessingFilter {

	private TokenAuthenticationService tokenAuthenticationService = new TokenAuthenticationServiceImpl();
	static UserDAO userDAO = Application.context.getBean("UserDAO", UserDAO.class);

	public JWTLoginFilter(String url, AuthenticationManager authManager) {
		super(new AntPathRequestMatcher(url));
		setAuthenticationManager(authManager);
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest req, HttpServletResponse res)
			throws AuthenticationException, IOException, ServletException {
		// System.out.println("body:\n" + req.getParameter("username") +"\n" +
		// req.getParameter("password"));
		// User user = new ObjectMapper().readValue(req.getInputStream(), User.class);

		return getAuthenticationManager().authenticate(new UsernamePasswordAuthenticationToken(
				req.getParameter("username"), req.getParameter("password"), Collections.emptyList()));
	}

	@Override
	protected void successfulAuthentication(HttpServletRequest req, HttpServletResponse res, FilterChain chain,
			Authentication auth) throws IOException, ServletException {
		tokenAuthenticationService.addAuthentication(res, auth);
		String username = auth.getName();
		Map<String, Object> currentUser = userDAO.getCurrentUserInfo(username);
		String active = currentUser.get("active").toString();
		if (active.equals("false")) {
			String verifyBody = new String(Files.readAllBytes(Paths.get(Config.getConfig("mail.verify.path"))),
					StandardCharsets.UTF_8);
			String verify = userDAO.getVerifyLink(username);
			String email = userDAO.getEmail(username);
			MailService.sendMail(email, "Threadripper: Verify account", verifyBody.replace("{{action_url}}", verify));
		}
		currentUser.put("avatarUrl", currentUser.get("avatarUrl"));
		currentUser.remove("active");
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("active", Boolean.valueOf(active));
		response.put("user", currentUser);
		ObjectMapper objectMapper = new ObjectMapper();

		res.getOutputStream().write(objectMapper.writeValueAsString(response).getBytes("UTF-8"));
	}

}
