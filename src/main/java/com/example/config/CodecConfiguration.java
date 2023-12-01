package com.example.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.codec.CodecCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.codec.ResourceRegionMessageWriter;

@Configuration
public class CodecConfiguration {

	@Autowired
	private ResourceRegionMessageWriter resourceRegionMessageWriter;

	@Bean
	CodecCustomizer myCustomCodecCustomizer() {
		return configurer -> configurer.customCodecs().register(resourceRegionMessageWriter);
	}
}