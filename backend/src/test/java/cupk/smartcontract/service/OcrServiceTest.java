package cupk.smartcontract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cupk.smartcontract.config.OcrProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcrServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OcrService service = new OcrService(new OcrProperties(
            null, null, null, 0, false, null, null, 0,
            null, null, null, null, null, null,
            false, false, false
    ), objectMapper, new OcrEditorHtmlService(objectMapper));

    @Test
    void readsBlockBboxBeforeFallbackFields() throws Exception {
        JsonNode candidate = objectMapper.readTree("""
                {"block_bbox":[90,180,510,215],"bbox":[1,2,3,4]}
                """);

        Object result = ReflectionTestUtils.invokeMethod(service, "readBbox", candidate);

        assertThat(readBbox(result)).containsExactly(90.0, 180.0, 510.0, 215.0);
        assertThat(readSource(result)).isEqualTo("block_bbox");
    }

    @Test
    void normalizesPolygonAndObjectFormats() throws Exception {
        assertBbox("[10,20,40,20,40,60,10,60]", 10, 20, 40, 60);
        assertBbox("[[10,20],[40,20],[40,60],[10,60]]", 10, 20, 40, 60);
        assertBbox("{\"x\":10,\"y\":20,\"width\":30,\"height\":40}", 10, 20, 40, 60);
        assertBbox("{\"left\":10,\"top\":20,\"right\":40,\"bottom\":60}", 10, 20, 40, 60);
    }

    @Test
    void fallsBackAfterInvalidPreferredField() throws Exception {
        JsonNode candidate = objectMapper.readTree("""
                {"block_bbox":[1,2,3],"coordinates":[10,20,40,60]}
                """);

        Object result = ReflectionTestUtils.invokeMethod(service, "readBbox", candidate);

        assertThat(readBbox(result)).containsExactly(10.0, 20.0, 40.0, 60.0);
        assertThat(readSource(result)).isEqualTo("coordinates");
    }

    @Test
    void readsPageSizeFromPrunedResultFirst() throws Exception {
        JsonNode result = objectMapper.readTree("""
                {
                  "width": 800,
                  "height": 900,
                  "prunedResult": {"width": "1191", "height": 1684.0}
                }
                """);

        Object pageSize = ReflectionTestUtils.invokeMethod(service, "readPageSize", result);

        assertThat(ReflectionTestUtils.<Integer>invokeMethod(pageSize, "width")).isEqualTo(1191);
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(pageSize, "height")).isEqualTo(1684);
        assertThat(ReflectionTestUtils.<String>invokeMethod(pageSize, "source"))
                .isEqualTo("prunedResult.width_height");
    }

    @Test
    void doesNotRecursivelyReadUnrelatedWidthAndHeight() throws Exception {
        JsonNode result = objectMapper.readTree("""
                {"table":{"width":1191,"height":1684}}
                """);

        Object pageSize = ReflectionTestUtils.invokeMethod(service, "readPageSize", result);

        assertThat(ReflectionTestUtils.<Integer>invokeMethod(pageSize, "width")).isNull();
        assertThat(ReflectionTestUtils.<Integer>invokeMethod(pageSize, "height")).isNull();
    }

    private void assertBbox(String value, double... expected) throws Exception {
        JsonNode candidate = objectMapper.readTree("{\"block_bbox\":" + value + "}");
        Object result = ReflectionTestUtils.invokeMethod(service, "readBbox", candidate);
        assertThat(readBbox(result)).containsExactly(toObjects(expected));
    }

    @SuppressWarnings("unchecked")
    private List<Double> readBbox(Object result) {
        return (List<Double>) ReflectionTestUtils.invokeMethod(result, "bbox");
    }

    private String readSource(Object result) {
        return ReflectionTestUtils.invokeMethod(result, "source");
    }

    private Double[] toObjects(double[] values) {
        Double[] result = new Double[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i];
        return result;
    }
}
