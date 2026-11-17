public class OrtLoadTest {
    public static void main(String[] args) {
        try {
            ai.onnxruntime.OrtEnvironment env = ai.onnxruntime.OrtEnvironment.getEnvironment();
            System.out.println("ONNX_LOAD_OK version=" + env.getVersion());
        } catch (Throwable t) {
            System.out.println("ONNX_LOAD_FAIL " + t);
        }
    }
}
