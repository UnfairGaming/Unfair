uniform float TIME;
uniform vec2 RESOLUTION;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash21(vec2 p) {
    p = fract(p * vec2(234.56, 345.67));
    p += dot(p, p + 67.89);
    return fract(p.x * p.y * 123.45);
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

float noiseDetail(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += noise(p) * a;
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

float fbmWarped(vec2 p) {
    vec2 q = vec2(fbm(p + vec2(0.0, 0.0)),
                  fbm(p + vec2(5.2, 1.3)));
    vec2 r = vec2(fbm(p + q * 1.2 + vec2(1.7, 9.2)),
                  fbm(p + q * 1.2 + vec2(8.3, 2.8)));
    return fbm(p + r * 0.8);
}

float fbmDetail(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        v += noiseDetail(p) * a;
        p = p * 2.17 + vec2(13.7, 21.3);
        a *= 0.5;
    }
    return v;
}

float speckleLayer(vec2 p, float scale, float threshold, float minRadius, float maxRadius, vec2 drift) {
    vec2 grid = p * scale;
    vec2 baseCell = floor(grid);
    vec2 local = fract(grid);
    float result = 0.0;

    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 offset = vec2(float(x), float(y));
            vec2 cell = baseCell + offset;
            float seed = hash(cell);
            vec2 center = vec2(hash(cell + vec2(4.7, 2.1)), hash(cell + vec2(8.3, 6.9))) + drift;
            float radius = mix(minRadius, maxRadius, hash(cell + vec2(11.8, 3.4)));
            float dotShape = 1.0 - smoothstep(radius * 0.55, radius, length(local - offset - center));
            result = max(result, dotShape * step(threshold, seed));
        }
    }
    return result;
}

float fiberTexture(vec2 p, float time) {
    float angle1 = 0.3 * sin(p.x * 80.0 + p.y * 20.0 + time * 0.2);
    float angle2 = 0.4 * cos(p.y * 70.0 - p.x * 30.0 + time * 0.15);
    float angle3 = 0.5 * sin(p.x * 45.0 + p.y * 55.0 - time * 0.18);
    float fiber = sin(p.x * 120.0 + p.y * 40.0 + angle1 * 2.0) * 0.5 + 0.5;
    fiber *= sin(p.y * 100.0 - p.x * 50.0 + angle2 * 1.8) * 0.5 + 0.5;
    fiber *= 0.7 + 0.3 * sin(p.x * 160.0 + p.y * 30.0 + angle3 * 1.5);
    return clamp(fiber, 0.0, 1.0);
}

float paperTexture(vec2 p) {
    float base = fbm(p * 3.0 + vec2(0.5, 1.2));
    float fine = fbmDetail(p * 8.0 + vec2(3.7, 5.1));
    return mix(base, fine, 0.3);
}

void main() {
    vec2 uv = gl_FragCoord.xy / RESOLUTION.xy;
    vec2 aspectUv = vec2(gl_FragCoord.x / RESOLUTION.y, gl_FragCoord.y / RESOLUTION.y);
    float t = TIME * 0.045;

    float paper = paperTexture(aspectUv);
    float grain = hash(gl_FragCoord.xy * 2.0 + 0.5);
    float cloudy = fbm(aspectUv * 1.55 + vec2(12.0 + t * 0.18, 4.0 - t * 0.12));

    float warpCloud = fbmWarped(aspectUv * 2.2 + vec2(t * 0.08, -t * 0.05));

    float detailNoise = noiseDetail(aspectUv * 35.0 + vec2(t * 0.02, t * 0.01)) * 0.5 + 0.5;

    float fiber = fiberTexture(aspectUv * 1.8 + vec2(t * 0.03, t * 0.02), t);

    float glow = 0.5 + 0.5 * sin(t * 0.5 + length(aspectUv - 0.5) * 4.0);

    float microTexture = fbmDetail(aspectUv * 15.0 + vec2(t * 0.01, t * 0.008)) * 0.5 + 0.5;

    vec3 color = vec3(0.639, 0.647, 0.635);

    color += (paper - 0.5) * vec3(0.035, 0.033, 0.030);
    color += (cloudy - 0.5) * vec3(0.025, 0.023, 0.020);
    color += (grain - 0.5) * 0.012;

    color += (warpCloud - 0.5) * vec3(0.018, 0.016, 0.014);
    color += (detailNoise - 0.5) * 0.008;
    color += (fiber - 0.5) * vec3(0.010, 0.009, 0.008);
    color += (glow - 0.5) * 0.005;
    color += (microTexture - 0.5) * 0.004;

    vec2 driftA = vec2(sin(t * 0.70), cos(t * 0.62)) * 0.055;
    vec2 driftB = vec2(cos(t * 0.48), sin(t * 0.54)) * 0.070;
    vec2 driftC = vec2(sin(t * 0.33 + 1.8), cos(t * 0.37 + 0.6)) * 0.085;
    vec2 driftD = vec2(cos(t * 0.76 + 2.2), sin(t * 0.69 + 1.1)) * 0.050;

    float dust = speckleLayer(aspectUv + vec2(3.2 + t * 0.16, 8.4 - t * 0.10), 44.0, 0.66, 0.030, 0.090, driftA);
    float pepper = speckleLayer(aspectUv + vec2(19.3 - t * 0.12, 7.4 + t * 0.15), 25.0, 0.72, 0.060, 0.150, driftB);
    float blotch = speckleLayer(aspectUv + vec2(41.0 + t * 0.08, 15.0 + t * 0.11), 12.0, 0.80, 0.115, 0.260, driftC);
    float pale = speckleLayer(aspectUv + vec2(5.1 - t * 0.18, 14.8 - t * 0.07), 31.0, 0.76, 0.055, 0.135, driftD);

    color = mix(color, vec3(0.500, 0.508, 0.488), dust * 0.22);
    color = mix(color, vec3(0.430, 0.438, 0.418), pepper * 0.36);
    color = mix(color, vec3(0.385, 0.394, 0.374), blotch * 0.32);
    color = mix(color, vec3(0.710, 0.716, 0.692), pale * 0.15);

    float vignette = smoothstep(0.98, 0.20, distance(uv, vec2(0.5)));
    float pulse = 1.0 + 0.015 * sin(t * 0.3);
    color *= (0.86 + vignette * 0.18) * pulse;

    float grainStrength = 0.015 + 0.01 * (1.0 - uv.y);
    color += (hash(gl_FragCoord.xy + t * 1000.0) - 0.5) * grainStrength;

    color = clamp(color, 0.0, 1.0);
    color = pow(color, vec3(1.0 / 1.02));

    gl_FragColor = vec4(color, 1.0);
}