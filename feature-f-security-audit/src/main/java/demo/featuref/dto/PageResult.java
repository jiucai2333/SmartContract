package demo.featuref.dto;

import java.util.List;

public record PageResult<T>(long page, long size, long total, List<T> records) {
}
