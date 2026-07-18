package org.example.aisurv.app;

import org.example.aisurv.camera.CameraApplicationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationContextTest {
    @Test
    void parsesNamedAndUnnamedCameraListEntries() throws Exception {
        List<CameraDefinition> cameras = invokeParser(
                "parseCameraList",
                new Class<?>[]{String.class},
                " Front Door = rtsp://front ; rtsp://loading ; ; Garage=rtsp://garage "
        );

        assertEquals(List.of("Front Door", "Camera 2", "Garage"), cameras.stream().map(CameraDefinition::name).toList());
        assertEquals(List.of("rtsp://front", "rtsp://loading", "rtsp://garage"), cameras.stream().map(CameraDefinition::rtspUrl).toList());
    }

    @Test
    void parsesNumberedCamerasInNumericOrderAndIgnoresInvalidEntries() throws Exception {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("CAM_10_URL", "rtsp://ten");
        environment.put("CAM_2_URL", "rtsp://two");
        environment.put("CAM_2_NAME", "Second Camera");
        environment.put("CAM_3_URL", "  ");
        environment.put("CAM_X_URL", "rtsp://invalid");

        List<CameraDefinition> cameras = invokeParser(
                "parseNumberedCameraEnvironment",
                new Class<?>[]{Map.class},
                environment
        );

        assertEquals(List.of("Second Camera", "Camera 10"), cameras.stream().map(CameraDefinition::name).toList());
        assertEquals(List.of("rtsp://two", "rtsp://ten"), cameras.stream().map(CameraDefinition::rtspUrl).toList());
    }

    @Test
    void usesEnvironmentOnlyWhenDatabaseIsUnavailableAndFallbackIsAllowed() {
        Map<String, String> environment = Map.of("AISURV_CAMERAS", "Gate=rtsp://gate");
        List<CameraDefinition> databaseCameras = List.of(new CameraDefinition("Registry", "rtsp://registry"));

        assertEquals(databaseCameras,
                ApplicationContext.selectCameras(databaseCameras, true, environment, true));
        assertEquals(List.of(),
                ApplicationContext.selectCameras(List.of(), true, environment, true));
        assertEquals(List.of(),
                ApplicationContext.selectCameras(List.of(), false, environment, false));
        assertEquals(List.of("Gate"), ApplicationContext.selectCameras(List.of(), false, environment, true)
                 .stream().map(CameraDefinition::name).toList());
    }

    @Test
    void loadsEnabledCamerasThroughTheApplicationService() {
        CameraApplicationService cameraService = mock(CameraApplicationService.class);
        CameraDefinition entrance = new CameraDefinition("Entrance", "rtsp://entrance/live");
        when(cameraService.listEnabledCameras()).thenReturn(List.of(entrance));

        ApplicationContext context = new ApplicationContext(cameraService);

        assertEquals(List.of(entrance), context.cameras());
        assertEquals(ApplicationContext.DatabaseState.CONNECTED, context.databaseState());
        assertEquals(ApplicationContext.CameraSource.DATABASE, context.cameraSource());
        verify(cameraService).listEnabledCameras();
    }

    @Test
    void doesNotSwallowArbitraryCameraServiceFailures() {
        CameraApplicationService cameraService = mock(CameraApplicationService.class);
        IllegalStateException failure = new IllegalStateException("unexpected mapping failure");
        when(cameraService.listEnabledCameras()).thenThrow(failure);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> new ApplicationContext(cameraService)
        );

        assertSame(failure, thrown);
    }

    @Test
    void recognizesOnlyConnectivityFailuresAsDatabaseUnavailability() {
        assertEquals(true, ApplicationContext.isDatabaseUnavailable(
                new IllegalStateException(new SQLException("connection refused", "08001"))));
        assertEquals(false, ApplicationContext.isDatabaseUnavailable(
                new IllegalStateException(new SQLException("schema validation failed", "42P01"))));
        assertEquals(false, ApplicationContext.isDatabaseUnavailable(
                new IllegalStateException("migration checksum mismatch")));
    }

    @SuppressWarnings("unchecked")
    private static List<CameraDefinition> invokeParser(String name, Class<?>[] parameterTypes, Object argument) throws Exception {
        Method method = ApplicationContext.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return (List<CameraDefinition>) method.invoke(null, argument);
    }
}
