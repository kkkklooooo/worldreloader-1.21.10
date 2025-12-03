#version 150

in vec4 vertexColor;
in vec2 texCoord0; // u = 长度方向, v = 宽度方向

out vec4 fragColor;

void main() {
    // texCoord0.y (即 V 坐标) 范围是 0.0 到 1.0
    // 我们希望 0.5 (中心) 最亮，0.0 和 1.0 (边缘) 最暗

    // 1. 计算距离中心的距离 (范围 0.0 ~ 1.0)
    float distanceFromCenter = abs(texCoord0.y - 0.5) * 2.0;

    // 2. 计算发光强度 (使用指数函数让中心极亮，边缘柔和)
    // pow(x, 3.0) 会让边缘衰减得更快，看起来像激光
    float intensity = pow(1.0 - distanceFromCenter, 2.5);

    // 3. 基础裁切 (可选)
    if (intensity < 0.01) discard;

    // 4. 合成颜色
    // 注意：我们将 alpha 也乘上 intensity，这样边缘就透明了
    vec4 color = vertexColor;
    color.a *= intensity;

    fragColor = color;
}