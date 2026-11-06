package dev.better.sheet.sample

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi

/** Clear glass over a screen-resolution sibling capture. All shader lengths are physical pixels. */
@RequiresApi(33)
internal class LensShader {
    private val shader = RuntimeShader("""
        uniform shader scene;
        uniform float2 size;
        uniform float radius;
        uniform float density;
        uniform float tilt;

        half4 main(float2 p) {
            float2 center = p - size * 0.5;
            float2 q = abs(center) - size * 0.5 + radius;
            float2 arc = max(q, 0.0);
            float depth = radius - length(arc) - min(max(q.x, q.y), 0.0);
            float2 axis = q.x > q.y ? float2(1, 0) : float2(0, 1);
            float2 normal = sign(center) * (length(arc) > 0.001 ? arc / max(length(arc), 0.001) : axis);

            // Circular bevel cross-section, then Snell refraction from air into glass.
            float bevel = min(15.0 * density, radius);
            float sinI = clamp(1.0 - depth / bevel, 0.0, 1.0);
            float cosI = sqrt(max(0.0, 1.0 - sinI * sinI));
            float sinT = sinI / 1.5;
            float cosT = sqrt(1.0 - sinT * sinT);
            float slope = (sinI * cosT - cosI * sinT) / max(0.01, cosI * cosT + sinI * sinT);
            float2 uv = p - normal * slope * bevel * (0.65 + cosI);
            float2 blur = float2(1.5, 1.5) * density;
            half3 color = scene.eval(uv).rgb * 0.40;
            color += 0.15 * (scene.eval(uv + blur).rgb + scene.eval(uv - blur).rgb
                          + scene.eval(uv + float2(blur.x, -blur.y)).rgb
                          + scene.eval(uv + float2(-blur.x, blur.y)).rgb);
            float light = dot(normal, normalize(float2(-0.55 + tilt * 0.45, -0.85)));
            float rim = exp(-max(depth, 0.0) / (1.25 * density));
            float shine = rim * (0.12 + 0.78 * pow(abs(light), 3.0));
            color = mix(color, half3(1.0), 0.065 + shine);
            return half4(color, 1);
        }
    """.trimIndent())

    fun setBackdrop(bitmap: Bitmap) {
        val input = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        input.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        shader.setInputShader("scene", input)
    }

    fun frame(w: Float, h: Float, corner: Float, density: Float, tilt: Float): Shader {
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("radius", corner)
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("tilt", tilt)
        return shader
    }
}
