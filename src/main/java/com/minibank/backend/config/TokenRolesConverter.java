package com.minibank.backend.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps JWT claim "roles" (array of strings) to Spring authorities.
 */
public class TokenRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		Object rolesObj = jwt.getClaims().get("roles");
		List<String> roles = new ArrayList<>();
		if (rolesObj instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof String role && !role.isBlank()) {
					roles.add(role.trim());
				}
			}
		} else if (rolesObj instanceof String roleStr && !roleStr.isBlank()) {
			roles.add(roleStr.trim());
		}

		List<GrantedAuthority> authorities = new ArrayList<>();
		for (String role : roles) {
			authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
		}
		return authorities;
	}
}
