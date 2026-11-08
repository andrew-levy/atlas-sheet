/*
 * Copyright 2025 Kyant
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 *
 * Modified for Better Sheet: single-radius View renderer, GPU blur, compact dispersion,
 * directional rim, padded input and alpha coverage. Refraction math adapted from
 * Kyant0/AndroidLiquidGlass backdrop/internal/Shaders.kt (Apache-2.0).
 */
package dev.better.sheet.sample

import android.graphics.*
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.ceil

/** Records only the padded lens region into a hardware display list; no CPU bitmap upload. */
@RequiresApi(33)
internal class LensShader {
    private val node = RenderNode("Glass backdrop")
    private val shader = RuntimeShader("""
        uniform shader content;
        uniform float2 size;
        uniform float padding;
        uniform float radius;
        uniform float height;
        uniform float amount;
        uniform float density;

        float sdf(float2 p, float r) {
            float2 q = abs(p) - size * 0.5 + r;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }
        float2 gradient(float2 p, float r) {
            float2 q = abs(p) - size * 0.5 + r;
            float2 arc = max(q, 0.0);
            float len = length(arc);
            return sign(p) * (len > 0.001 ? arc / len :
                (q.x > q.y ? float2(1, 0) : float2(0, 1)));
        }
        half4 main(float2 coord) {
            float2 p = coord - padding - size * 0.5;
            float sd = sdf(p, radius);
            float coverage = 1.0 - smoothstep(-0.75, 0.75, sd);
            if (coverage <= 0.0) return half4(0.0);
            float depth = max(-sd, 0.0);
            float t = clamp(1.0 - depth / max(height, 0.001), 0.0, 1.0);
            float bend = (1.0 - sqrt(max(0.0, 1.0 - t * t))) * amount;
            float2 normal = gradient(p, min(radius * 1.5, min(size.x, size.y) * 0.5));
            float2 uv = coord - normal * bend;
            // A restrained RGB split only in the bevel, leaving the center crisp.
            float2 dispersion = normal * bend * 0.035;
            half4 center = content.eval(uv);
            half4 red = content.eval(uv + dispersion);
            half4 blue = content.eval(uv - dispersion);
            half alpha = max(center.a, max(red.a, blue.a));
            half3 color = half3(red.r, center.g, blue.b);
            float rim = exp(-depth / max(0.8 * density, 0.001));
            float light = pow(abs(dot(normal, normalize(float2(-0.6, -0.8)))), 3.0);
            color = mix(color, half3(alpha), half(0.035 + rim * (0.12 + 0.62 * light)));
            return half4(min(color, half3(alpha)), alpha) * half(coverage);
        }
    """.trimIndent())
    private var previousParameters = floatArrayOf()
    private var effect: RenderEffect? = null

    fun draw(canvas: Canvas, source: View, matrix: Matrix, w: Int, h: Int,
             radius: Float, blur: Float, height: Float, amount: Float, density: Float) {
        val pad = ceil(blur * 3f + 2f).toInt()
        node.setPosition(0, 0, w + pad * 2, h + pad * 2)
        shader.setFloatUniform("size", w.toFloat(), h.toFloat())
        shader.setFloatUniform("padding", pad.toFloat())
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("height", height)
        shader.setFloatUniform("amount", amount)
        shader.setFloatUniform("density", density)
        val parameters = floatArrayOf(w.toFloat(), h.toFloat(), radius, blur, height, amount, density)
        if (!previousParameters.contentEquals(parameters)) {
            val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
            effect = if (blur > 0f) RenderEffect.createChainEffect(lens,
                RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)) else lens
            previousParameters = parameters
        }
        node.setRenderEffect(effect)
        val recording = node.beginRecording()
        try {
            recording.translate(pad.toFloat(), pad.toFloat())
            recording.concat(matrix)
            source.draw(recording)
        } finally {
            node.endRecording()
        }
        val save = canvas.save()
        canvas.translate(-pad.toFloat(), -pad.toFloat())
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
    }

    fun release() { node.discardDisplayList() }
}
