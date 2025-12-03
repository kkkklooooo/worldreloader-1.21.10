#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0; // 我们利用 UV 的 V 分量来确定宽度上的位置

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}