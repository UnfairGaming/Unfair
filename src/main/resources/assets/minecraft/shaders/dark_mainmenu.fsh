#version 120

uniform float TIME;
uniform vec2 RESOLUTION;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(41.37, 289.19))) * 45758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.55;
    mat2 r = mat2(0.86, -0.50, 0.50, 0.86);

    for (int i = 0; i < 5; i++) {
        v += noise(p) * a;
        p = r * p * 2.05 + vec2(11.7, 6.3);
        a *= 0.47;
    }
    return v;
}

float glow(vec2 p, vec2 center, float radius, float power) {
    float d = length((p - center) / radius);
    return pow(max(0.0, 1.0 - d), power);
}

void main() {
    vec2 uv = gl_FragCoord.xy / RESOLUTION.xy;
    vec2 p = (gl_FragCoord.xy * 2.0 - RESOLUTION.xy) / RESOLUTION.y;
    float t = TIME * 0.025;

    vec2 flow = vec2(fbm(p * 0.85 + vec2(t, -t * 0.7)), fbm(p * 0.85 - vec2(t * 0.8, t)));
    vec2 warp = (flow - 0.5) * 0.38;
    float smoke = fbm(p * 1.15 + warp + vec2(t * 0.45, -t * 0.30));
    float shade = fbm(p * 2.35 - warp * 0.7 - vec2(t * 0.22, t * 0.34));

    vec3 color = vec3(0.007, 0.009, 0.014);
    color = mix(color, vec3(0.024, 0.030, 0.041), smoothstep(0.12, 0.88, smoke));
    color += vec3(0.024, 0.052, 0.078) * smoothstep(0.45, 0.96, shade) * 0.78;
    color += vec3(0.078, 0.034, 0.056) * smoothstep(0.58, 1.0, smoke + shade * 0.25) * 0.34;

    float leftGlow = glow(p, vec2(-0.82, -0.24), 1.45, 2.4);
    float rightGlow = glow(p, vec2(0.88, 0.28), 1.35, 2.6);
    float lowGlow = glow(p, vec2(0.02, -0.72), 1.20, 2.8);
    color += vec3(0.052, 0.087, 0.118) * leftGlow * 0.92;
    color += vec3(0.105, 0.050, 0.082) * rightGlow * 0.58;
    color += vec3(0.030, 0.070, 0.060) * lowGlow * 0.44;

    float satin = smoothstep(0.48, 0.98, fbm(p * 3.2 + warp * 0.6 + vec2(-t, t * 0.65)));
    color += vec3(0.035, 0.045, 0.060) * satin * 0.36;

    float vignette = smoothstep(1.10, 0.18, distance(uv, vec2(0.5)));
    color *= 0.72 + vignette * 0.78;
    color += (hash(gl_FragCoord.xy + TIME * 9.0) - 0.5) * 0.012;

    gl_FragColor = vec4(max(color, vec3(0.0)), 1.0);
}
