package com.bellinicine.core.render

/**
 * GLSL ES 3.0 shaders for high-performance mobile compositing, blending, and visual effects.
 */
object ShaderSources {

    const val VERTEX_SHADER_QUAD = """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uProjection;
uniform mat4 uWorldMatrix;

out vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = uProjection * uWorldMatrix * vec4(aPosition, 0.0, 1.0);
}
"""

    const val FRAGMENT_SHADER_SOLID = """#version 300 es
precision mediump float;

uniform vec4 uColor;
uniform float uOpacity;

out vec4 fragColor;

void main() {
    fragColor = vec4(uColor.rgb, uColor.a * (uOpacity / 100.0));
}
"""

    const val FRAGMENT_SHADER_TEXTURE = """#version 300 es
precision mediump float;

in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform float uOpacity;

out vec4 fragColor;

void main() {
    vec4 tex = texture(uTexture, vTexCoord);
    fragColor = vec4(tex.rgb, tex.a * (uOpacity / 100.0));
}
"""

    const val FRAGMENT_SHADER_BLEND = """#version 300 es
precision mediump float;

in vec2 vTexCoord;
uniform sampler2D uBaseTexture;
uniform sampler2D uBlendTexture;
uniform int uBlendMode;
uniform float uOpacity;

out vec4 fragColor;

// After Effects Blend Mode Calculations
vec3 blendMultiply(vec3 base, vec3 blend) { return base * blend; }
vec3 blendScreen(vec3 base, vec3 blend) { return 1.0 - (1.0 - base) * (1.0 - blend); }
vec3 blendOverlay(vec3 base, vec3 blend) {
    return vec3(
        base.r < 0.5 ? (2.0 * base.r * blend.r) : (1.0 - 2.0 * (1.0 - base.r) * (1.0 - blend.r)),
        base.g < 0.5 ? (2.0 * base.g * blend.g) : (1.0 - 2.0 * (1.0 - base.g) * (1.0 - blend.g)),
        base.b < 0.5 ? (2.0 * base.b * blend.b) : (1.0 - 2.0 * (1.0 - base.b) * (1.0 - blend.b))
    );
}
vec3 blendDifference(vec3 base, vec3 blend) { return abs(base - blend); }
vec3 blendColorDodge(vec3 base, vec3 blend) {
    return vec3(
        blend.r == 1.0 ? 1.0 : min(1.0, base.r / (1.0 - blend.r)),
        blend.g == 1.0 ? 1.0 : min(1.0, base.g / (1.0 - blend.g)),
        blend.b == 1.0 ? 1.0 : min(1.0, base.b / (1.0 - blend.b))
    );
}

void main() {
    vec4 base = texture(uBaseTexture, vTexCoord);
    vec4 blend = texture(uBlendTexture, vTexCoord);
    
    vec3 result = blend.rgb;
    
    // 0: Normal, 1: Multiply, 2: Screen, 3: Overlay, 4: Add, 5: Difference
    if (uBlendMode == 1) {
        result = blendMultiply(base.rgb, blend.rgb);
    } else if (uBlendMode == 2) {
        result = blendScreen(base.rgb, blend.rgb);
    } else if (uBlendMode == 3) {
        result = blendOverlay(base.rgb, blend.rgb);
    } else if (uBlendMode == 4) {
        result = min(vec3(1.0), base.rgb + blend.rgb);
    } else if (uBlendMode == 5) {
        result = blendDifference(base.rgb, blend.rgb);
    }
    
    float alpha = blend.a * (uOpacity / 100.0);
    vec3 finalRgb = mix(base.rgb, result, alpha);
    fragColor = vec4(finalRgb, max(base.a, alpha));
}
"""

    const val FRAGMENT_SHADER_GAUSSIAN_BLUR = """#version 300 es
precision mediump float;

in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec2 uDirection; // (1.0/w, 0.0) or (0.0, 1.0/h)
uniform float uRadius;

out vec4 fragColor;

void main() {
    vec4 sum = vec4(0.0);
    vec2 offset = uDirection * uRadius;
    
    sum += texture(uTexture, vTexCoord - 4.0 * offset) * 0.051;
    sum += texture(uTexture, vTexCoord - 3.0 * offset) * 0.0918;
    sum += texture(uTexture, vTexCoord - 2.0 * offset) * 0.12245;
    sum += texture(uTexture, vTexCoord - 1.0 * offset) * 0.1531;
    sum += texture(uTexture, vTexCoord) * 0.1633;
    sum += texture(uTexture, vTexCoord + 1.0 * offset) * 0.1531;
    sum += texture(uTexture, vTexCoord + 2.0 * offset) * 0.12245;
    sum += texture(uTexture, vTexCoord + 3.0 * offset) * 0.0918;
    sum += texture(uTexture, vTexCoord + 4.0 * offset) * 0.051;
    
    fragColor = sum;
}
"""
}
