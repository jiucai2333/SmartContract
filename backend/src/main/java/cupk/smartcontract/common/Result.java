package cupk.smartcontract.common;

import lombok.Data;

@Data
public class Result {
    private Integer code;
    private String msg;
    private Object data;

    public static Result success(Object data) {
        Result result = new Result();
        result.setCode(200);
        result.setMsg("成功");
        result.setData(data);
        return result;
    }

    public static Result success() {
        return success(null);
    }

    public static Result error(String msg) {
        return error(400, msg);
    }

    public static Result error(Integer code, String msg) {
        Result result = new Result();
        result.setCode(code != null ? code : 400);
        result.setMsg(msg != null ? msg : "操作失败");
        result.setData(null);
        return result;
    }
}
