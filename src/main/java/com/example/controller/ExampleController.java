package com.example.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.ExampleOutputDto;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping
public class ExampleController {

	@GetMapping("/hello")
	public Mono<ResponseEntity<ExampleOutputDto>> hello() {
		ExampleOutputDto exampleOutputDto = new ExampleOutputDto();
		exampleOutputDto.setData1("Hello");
		exampleOutputDto.setData2("World");
		return Mono.just(ResponseEntity.ok().body(exampleOutputDto));
	}

	@GetMapping("/download")
	public Mono<ResponseEntity<ResourceRegion>> download(@RequestHeader HttpHeaders headers) throws IOException {

		File file = new File("C:\\path\\to\\file.txt");
		Resource resource = new FileSystemResource(file);

		HttpRange range = headers.getRange().stream().findFirst().orElse(null);
		HttpStatusCode httpStatusCode = HttpStatus.OK;

		long start = 0L;
		long end = resource.contentLength();

		if (range != null) {
			start = range.getRangeStart(resource.contentLength());
			end = range.getRangeEnd(resource.contentLength());
			httpStatusCode = HttpStatus.PARTIAL_CONTENT;
		}

		ResourceRegion region = new ResourceRegion(resource, start, end);

		return Mono.just(ResponseEntity.status(httpStatusCode).contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(region));

	}

	@PostMapping("/upload")
	public Mono<Void> handleFileUpload(@RequestPart("file") Mono<FilePart> filePartMono) {
		File folder = new File("C:\\path\\to\\folder");

		Path root = folder.toPath();
		return filePartMono.flatMap(filePart -> {
			String filename = filePart.filename();
			return filePart.transferTo(root.resolve(filename));
		});
	}
}
