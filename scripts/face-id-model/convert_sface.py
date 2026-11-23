"""Rebuild OpenCV Zoo SFace (face_recognition_sface_2021dec.onnx, Apache-2.0) as a Keras model
and export TFLite (float32 and dynamic-range int8). The network is a plain chain:
Sub(127.5) -> [Conv/DWConv(+folded BN, scale) -> PReLU] x27 -> BN -> Flatten(NCHW) -> Gemm -> BN.

Input: NHWC [1,112,112,3], RGB, 0..255 (OpenCV feeds blobFromImage(swapRB=true, scale 1)).
Output: [1,128] feature (not normalized).
"""
import sys
import numpy as np
import onnx
from onnx import numpy_helper, helper
import tensorflow as tf

src, out_dir = sys.argv[1], sys.argv[2]
m = onnx.load(src)
init = {i.name: numpy_helper.to_array(i) for i in m.graph.initializer}
nodes = list(m.graph.node)

inp = tf.keras.Input(shape=(112, 112, 3), batch_size=1, name="rgb")
x = inp
gemm = None
for n in nodes:
    a = {k.name: helper.get_attribute_value(k) for k in n.attribute}
    op = n.op_type
    if op == "Sub":
        c = float(init[n.input[1]].reshape(-1)[0])
        x = tf.keras.layers.Lambda(lambda t, c=c: t - c, name="normalize")(x)
    elif op == "Conv":
        w = init[n.input[1]]
        b = init[n.input[2]] if len(n.input) > 2 else np.zeros(w.shape[0], np.float32)
        stride = a["strides"][0]
        k = a["kernel_shape"][0]
        pad = a["pads"][0]
        assert a["pads"] == [pad] * 4 and a["dilations"] == [1, 1]
        group = a["group"]
        if pad and stride == 2:
            x = tf.keras.layers.ZeroPadding2D(pad)(x)
            padding = "valid"
        else:
            padding = "same" if pad else "valid"
            assert pad in (0, k // 2)
        if group == 1:
            layer = tf.keras.layers.Conv2D(w.shape[0], k, strides=stride, padding=padding, name=n.output[0])
            x = layer(x)
            layer.set_weights([w.transpose(2, 3, 1, 0), b])
        else:
            assert group == w.shape[0] and w.shape[1] == 1
            layer = tf.keras.layers.DepthwiseConv2D(k, strides=stride, padding=padding, name=n.output[0])
            x = layer(x)
            layer.set_weights([w.transpose(2, 3, 0, 1), b])
    elif op == "PRelu":
        slope = init[n.input[1]].reshape(-1)
        layer = tf.keras.layers.PReLU(shared_axes=[1, 2], name=n.output[0])
        x = layer(x)
        layer.set_weights([slope.reshape(1, 1, -1)])
    elif op == "BatchNormalization":
        g, beta, mean, var = (init[i] for i in n.input[1:5])
        scale = g / np.sqrt(var + a["epsilon"])
        shift = beta - mean * scale
        x = tf.keras.layers.Lambda(
            lambda t, s=scale.astype(np.float32), h=shift.astype(np.float32): t * s + h, name=n.output[0]
        )(x)
    elif op in ("Dropout",):
        pass
    elif op == "Flatten":
        # NHWC [1,7,7,1024] flattened h,w,c; the Gemm weights are reordered to match below.
        hwc = x.shape[1:]
        x = tf.keras.layers.Flatten()(x)
    elif op == "Gemm":
        w = init[n.input[1]]  # (128, C*H*W) in NCHW order, transB=1
        b = init[n.input[2]]
        H, W, C = hwc
        w = w.reshape(w.shape[0], C, H, W).transpose(0, 2, 3, 1).reshape(w.shape[0], -1)
        layer = tf.keras.layers.Dense(w.shape[0], name="fc")
        x = layer(x)
        layer.set_weights([w.T, b])
    else:
        raise SystemExit(f"unsupported op {op}")

model = tf.keras.Model(inp, x)

import onnxruntime as ort
sess = ort.InferenceSession(src)
rng = np.random.default_rng(7)
batch = rng.uniform(0, 255, size=(4, 112, 112, 3)).astype(np.float32)


def cos(a, b):
    a = a.reshape(-1); b = b.reshape(-1)
    return float(a @ b / np.linalg.norm(a) / np.linalg.norm(b))


ref = [sess.run(None, {"data": im[None].transpose(0, 3, 1, 2)})[0] for im in batch]
keras_out = [model(im[None]).numpy() for im in batch]
print("keras vs onnx cos:", [round(cos(r, k), 6) for r, k in zip(ref, keras_out)])

for name, quant in (("sface_f32.tflite", False), ("sface_drq.tflite", True)):
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    if quant:
        conv.optimizations = [tf.lite.Optimize.DEFAULT]
    data = conv.convert()
    path = f"{out_dir}/{name}"
    open(path, "wb").write(data)
    interp = tf.lite.Interpreter(model_path=path)
    interp.allocate_tensors()
    i_d, o_d = interp.get_input_details()[0], interp.get_output_details()[0]
    outs = []
    for im in batch:
        interp.set_tensor(i_d["index"], im[None])
        interp.invoke()
        outs.append(interp.get_tensor(o_d["index"]).copy())
    print(name, len(data), "bytes; input", i_d["shape"], i_d["dtype"], "output", o_d["shape"],
          "cos vs onnx:", [round(cos(r, o), 5) for r, o in zip(ref, outs)])
    ops = sorted({d["op_name"] for d in interp._get_ops_details()})
    print("  ops:", ops)
