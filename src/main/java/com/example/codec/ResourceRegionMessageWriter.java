package com.example.codec;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.ResourceRegionEncoder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class ResourceRegionMessageWriter implements HttpMessageWriter<ResourceRegion> {

	private static final ResolvableType REGION_TYPE = ResolvableType.forClass(ResourceRegion.class);

	private final ResourceRegionEncoder regionEncoder = new ResourceRegionEncoder();

	private final List<MediaType> mediaTypes = MediaType.asMediaTypes(regionEncoder.getEncodableMimeTypes());

	@Override
	public boolean canWrite(ResolvableType elementType, MediaType mediaType) {
		return regionEncoder.canEncode(elementType, mediaType);
	}

	@Override
	public List<MediaType> getWritableMediaTypes() {
		return mediaTypes;
	}

	@Override
	public Mono<Void> write(Publisher<? extends ResourceRegion> inputStream, ResolvableType elementType, MediaType mediaType, ReactiveHttpOutputMessage message, Map<String, Object> hints) {
		throw new UnsupportedOperationException("Use server-specific write method");
	}

	@Override
	public Mono<Void> write(Publisher<? extends ResourceRegion> inputStream, ResolvableType actualType, ResolvableType elementType, MediaType mediaType, ServerHttpRequest request, ServerHttpResponse response, Map<String, Object> hints) {

		HttpHeaders headers = response.getHeaders();
		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

		return Mono.from(inputStream).flatMap(resourceRegion -> {
			response.setStatusCode(HttpStatus.PARTIAL_CONTENT);
			MediaType resourceMediaType = getResourceMediaType(mediaType, resourceRegion.getResource());
			headers.setContentType(resourceMediaType);

			long contentLength;
			try {
				contentLength = resourceRegion.getResource().contentLength();
			} catch (IOException e) {
				return Mono.error(e);
			}
			long start = resourceRegion.getPosition();
			long end = Math.min(start + resourceRegion.getCount() - 1, contentLength - 1);
			headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + '-' + end + '/' + contentLength);
			headers.setContentLength(end - start + 1);

			String disposition = "inline";
			if (!resourceMediaType.toString().startsWith("image")) {
				disposition = request.getHeaders().getAccept().stream().anyMatch(accept -> accept == resourceMediaType) ? "inline" : "attachment";
			}
			headers.add(HttpHeaders.CONTENT_DISPOSITION, disposition + ";filename=\"" + encodeFileName(resourceRegion.getResource().getFilename()) + "\"");

			return zeroCopy(resourceRegion.getResource(), resourceRegion, response).orElseGet(() -> {
				Mono<ResourceRegion> input = Mono.just(resourceRegion);
				Publisher<DataBuffer> body = this.regionEncoder.encode(input, response.bufferFactory(), REGION_TYPE, resourceMediaType, Collections.emptyMap());
				return response.writeWith(body);
			});
		});
	}

	private static String encodeFileName(String fileName) {
		try {
			return URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
		} catch (Exception e) {
			return fileName;
		}
	}

	private static MediaType getResourceMediaType(MediaType mediaType, Resource resource) {
		return mediaType != null && mediaType.isConcrete() && mediaType != MediaType.APPLICATION_OCTET_STREAM ? mediaType : MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
	}

	private static Optional<Mono<Void>> zeroCopy(Resource resource, ResourceRegion region, ReactiveHttpOutputMessage message) {
		if (message instanceof ZeroCopyHttpOutputMessage && resource.isFile()) {
			try {
				ZeroCopyHttpOutputMessage zeroCopyMessage = (ZeroCopyHttpOutputMessage) message;
				return Optional.of(zeroCopyMessage.writeWith(resource.getFile(), region.getPosition(), region.getCount()));
			} catch (IOException e) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}
}
