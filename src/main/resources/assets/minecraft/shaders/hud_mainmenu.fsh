#version 120

uniform float TIME;
uniform vec2 RESOLUTION;
uniform vec3 HUD_COLOR;

float glow(vec2 uv, vec2 center, float spread) {
    float d = length(uv - center) * spread;
    return exp(-d * d);
}

void main() {
    vec2 uv = gl_FragCoord.xy / RESOLUTION;
    vec3 base = mix(vec3(0.028, 0.028, 0.045), vec3(0.010, 0.010, 0.020), uv.y);
    float pulse = 0.5 + 0.5 * sin(TIME * 0.6);
    float mainGlow = glow(uv, vec2(0.5, 0.16), 2.2);
    float topGlow = glow(uv, vec2(0.5, 0.98), 3.2) * 0.55;
    float sideGlow = glow(uv, vec2(0.02, 0.5), 3.0) * 0.3;
    float total = mainGlow * (0.32 + 0.14 * pulse) + topGlow + sideGlow;
    vec3 color = base + HUD_COLOR * total * 0.16;
    gl_FragColor = vec4(color, 1.0);
}