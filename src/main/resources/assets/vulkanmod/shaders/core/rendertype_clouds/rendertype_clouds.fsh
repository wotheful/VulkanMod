#version 450

#include "fog.glsl"

layout(binding = 1) uniform UBO{
    vec4 FogColor;
    float FogStart;
    float FogEnd;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in float vertexDistance;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = linear_fog(vertexColor, vertexDistance, FogStart, FogEnd, FogColor);
}

//#version 150
//
//#moj_import <fog.glsl>
//
//uniform sampler2D Sampler0;
//
//uniform vec4 ColorModulator;
//uniform float FogStart;
//uniform float FogEnd;
//uniform vec4 FogColor;
//
//in vec2 texCoord0;
//in float vertexDistance;
//in vec4 vertexColor;
//
//out vec4 fragColor;
//
//void main() {
//    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
//    if (color.a < 0.1) {
//        discard;
//    }
//    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
//}
