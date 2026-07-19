package org.example.aisurv.edge.api.v1;

import jakarta.servlet.http.HttpServletRequest;
import org.example.aisurv.contract.v1.ApiProblemV1;
import org.example.aisurv.edge.service.DependencyUnavailableException;
import org.example.aisurv.edge.service.CameraDiscoveryFailedException;
import org.example.aisurv.edge.service.OperationInProgressException;
import org.example.aisurv.camera.CameraNotFoundException;
import org.example.aisurv.camera.CameraVersionConflictException;
import org.example.aisurv.camera.DuplicateCameraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DependencyUnavailableException.class)
    ResponseEntity<ApiProblemV1> dependencyUnavailable(DependencyUnavailableException failure,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiProblemV1(
                "DEPENDENCY_UNAVAILABLE", failure.getMessage(), request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiProblemV1> invalidRequest(RuntimeException failure, HttpServletRequest request) {
        String message = failure instanceof IllegalArgumentException
                ? failure.getMessage() : "The request body is invalid";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiProblemV1(
                "INVALID_REQUEST", message, request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(OperationInProgressException.class)
    ResponseEntity<ApiProblemV1> operationInProgress(OperationInProgressException failure,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiProblemV1(
                "OPERATION_IN_PROGRESS", failure.getMessage(), request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(CameraDiscoveryFailedException.class)
    ResponseEntity<ApiProblemV1> discoveryFailed(CameraDiscoveryFailedException failure,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiProblemV1(
                "DISCOVERY_FAILED", failure.getMessage(), request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(CameraNotFoundException.class)
    ResponseEntity<ApiProblemV1> notFound(CameraNotFoundException failure, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiProblemV1("CAMERA_NOT_FOUND", failure.getMessage(), request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(CameraVersionConflictException.class)
    ResponseEntity<ApiProblemV1> versionConflict(CameraVersionConflictException failure, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(new ApiProblemV1("CAMERA_VERSION_CONFLICT", failure.getMessage(), request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(DuplicateCameraException.class)
    ResponseEntity<ApiProblemV1> duplicate(DuplicateCameraException failure, HttpServletRequest request) {
        String code = failure.field() == DuplicateCameraException.Field.DISPLAY_NAME ? "DUPLICATE_DISPLAY_NAME" : "DUPLICATE_STREAM";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiProblemV1(code, failure.getMessage(), request.getRequestURI(), Instant.now()));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiProblemV1> internalError(RuntimeException failure, HttpServletRequest request) {
        LOGGER.error("Unhandled edge API failure for {}", request.getRequestURI(), failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiProblemV1(
                "INTERNAL_ERROR", "The edge service could not complete the request",
                request.getRequestURI(), Instant.now()));
    }
}
