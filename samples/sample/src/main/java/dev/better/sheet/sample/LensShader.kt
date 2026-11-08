/*
 * Copyright 2025 Kyant
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Adapted from Kyant0/AndroidLiquidGlass. Additional dual-lens material,
 * liquid SDF union, touch lighting and cropped recording authored for Better Sheet.
 * The geometry/motion design also takes inspiration from rit3zh/expo-glass-custom-menu
 * (MIT), without depending on its menu framework.
 */
package dev.better.sheet.sample

import android.graphics.*
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Two optical refraction profiles + a physically rounded or smooth-unioned
 * signed-distance field. Records only the illuminated/refracted viewport region,
 * NOT a full-window GPU surface when the glass overlay covers the screen.
 */
@RequiresApi(33)
internal class LensShader {
    private val node = RenderNode("BetterSheet liquid glass")
    private val shader = RuntimeShader("""
        uniform shader content;
        uniform float2 origin;
        uniform float2 sampleExtent;
        uniform float4 firstBounds;
        uniform float4 secondBounds;
        uniform float firstRadius;
        uniform float secondRadius;
        uniform float merge;
        uniform float smoothness;
        uniform float2 lean;
        uniform float press;
        uniform float2 lightPosition;
        uniform float glow;
        uniform float density;
        uniform float innerHeight;
        uniform float innerAmount;
        uniform float outerHeight;
        uniform float outerAmount;

        float3 roundField(float2 point, float4 rect, float rad) {
            float2 center = (rect.xy + rect.zw) * 0.5;
            float2 halfExtent = max((rect.zw - rect.xy) * 0.5, float2(0.5));
            float r = clamp(rad, 0.0, min(halfExtent.x, halfExtent.y));
            float2 signedCenter = point - center;
            float2 q = abs(signedCenter) - halfExtent + r;
            float2 arc = max(q, 0.0);
            float outside = length(arc);
            float distance = outside + min(max(q.x, q.y), 0.0) - r;
            float2 normal = outside > 0.0001 ? arc / outside :
                (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
            normal *= float2(signedCenter.x < 0.0 ? -1.0 : 1.0,
                             signedCenter.y < 0.0 ? -1.0 : 1.0);
            return float3(distance, normalize(normal));
        }

        float3 field(float2 point) {
            // Press gives a small swell; dragging stretches the edge rather than
            // applying a uniform scale to a rigid rectangle.
            float2 shift = lean * 0.17;
            float2 stretch = (4.0 * press * density) + abs(lean) * 0.28;
            float4 first = firstBounds + float4(shift - stretch, shift + stretch);
            float3 a = roundField(point, first, firstRadius + 2.0 * press * density);
            if (merge <= 0.0001) return a;
            float3 b = roundField(point, secondBounds, secondRadius);
            float k = smoothness * merge;
            if (k <= 0.0001) return b.x < a.x ? b : a;
            float h = clamp(0.5 + 0.5 * (b.x - a.x) / k, 0.0, 1.0);
            float distance = mix(b.x, a.x, h) - k * h * (1.0 - h);
            float2 normal = mix(b.yz, a.yz, h);
            float len = length(normal);
            return float3(distance, len > 0.0001 ? normal / len : a.yz);
        }

        half4 sampleBackdrop(float2 uv) {
            return content.eval(clamp(uv - origin, float2(0.5), sampleExtent - float2(0.5)));
        }

        float bend(float depth, float height, float amount) {
            float t = clamp(depth / max(height, 0.001), 0.0, 1.0);
            return amount * (1.0 - sqrt(max(0.0, t * (2.0 - t))));
        }

        half4 main(float2 coord) {
            float2 p = coord + origin;
            float3 shape = field(p);
            float dist = shape.x;
            float2 normal = shape.yz;
            float aa = max(abs(normal.x) + abs(normal.y), 0.75);
            float coverage = clamp(0.5 - dist / aa, 0.0, 1.0);
            if (coverage <= 0.0) return half4(0.0);

            float depth = max(-dist, 0.0);
            float inner = bend(depth, innerHeight, innerAmount);
            float outerWeight = clamp((dist + 1.5 * density) / (2.5 * density), 0.0, 1.0);
            float outer = bend(depth, outerHeight, outerAmount) * outerWeight;
            float2 displace = normal * (inner + outer);
            float2 uv = coord + origin + displace;
            float2 split = normal * abs(inner + outer) * 0.028;
            half4 center = sampleBackdrop(uv);
            half4 red = sampleBackdrop(uv + split);
            half4 blue = sampleBackdrop(uv - split);

            float3 rgb = float3(red.r, center.g, blue.b);
            float luminance = dot(rgb, float3(.2126, .7152, .0722));
            // Mild saturation and neutral frost without obscuring fine detail.
            rgb = mix(float3(luminance), rgb, 1.14);
            rgb = mix(rgb, float3(.91, .95, 1.0), 0.055);

            float2 keyDirection = normalize(float2(-0.60, -0.80));
            float key = pow(max(dot(normal, keyDirection), 0.0), 4.0);
            float fill = pow(max(dot(normal, -keyDirection), 0.0), 5.0);
            float thinRim = exp(-depth / max(1.5 * density, 0.001));
            float wideRim = exp(-depth / max(8.0 * density, 0.001));
            float shine = thinRim * (0.14 + 0.56 * key + 0.14 * fill) +
                wideRim * (0.045 + 0.12 * key);
            rgb = mix(rgb, float3(1.0), clamp(shine, 0.0, .85));

            float2 touch = p - lightPosition;
            float touchRadius = max(58.0 * density, 1.0);
            float pool = exp(-dot(touch, touch) / (2.0 * touchRadius * touchRadius));
            rgb += pool * glow * 0.17;
            float alpha = max(float(center.a), max(float(red.a), float(blue.a)));
            return half4(half3(clamp(rgb, float3(0.0), float3(1.0))), half(alpha))
                * half(coverage);
        }
    """.trimIndent())
    // HWUI snapshots the shader's uniform state as part of RenderEffect.
    // A mutable RuntimeShader alone does not reliably invalidate an existing
    // RenderEffect when refraction, geometry, or lighting parameters change.
    private var lastEffectParameters = floatArrayOf()
    private var cachedEffect: RenderEffect? = null

    fun draw(
        canvas: Canvas, source: View, sourceMatrix: Matrix,
        width: Int, height: Int, motion: GlassMotion,
        blur: Float, innerHeight: Float, innerAmount: Float, density: Float
    ) {
        val first = motion.primary
        val second = motion.secondary
        val capture = RectF(first)
        if (motion.mergeAmount > 0.001f) capture.union(second)
        // Capture all displaced samples, even when the edge pulls outside the pill.
        val outerHeight = max(14f * density, innerHeight * .75f)
        val outerAmount = innerAmount * .30f
        val guard = ceil(max(blur * 3f, max(abs(innerAmount), abs(outerAmount))) +
            8f * density).toFloat()
        capture.inset(-guard, -guard)
        capture.intersect(0f, 0f, width.toFloat(), height.toFloat())
        if (capture.isEmpty) return
        val x = capture.left.toInt()
        val y = capture.top.toInt()
        val w = min(width - x, ceil(capture.right).toInt() - x)
        val h = min(height - y, ceil(capture.bottom).toInt() - y)
        if (w <= 0 || h <= 0) return

        shader.setFloatUniform("origin", x.toFloat(), y.toFloat())
        shader.setFloatUniform("sampleExtent", w.toFloat(), h.toFloat())
        shader.setFloatUniform("firstBounds", first.left, first.top, first.right, first.bottom)
        shader.setFloatUniform("secondBounds", second.left, second.top, second.right, second.bottom)
        shader.setFloatUniform("firstRadius", motion.primaryRadius)
        shader.setFloatUniform("secondRadius", motion.secondaryRadius)
        shader.setFloatUniform("merge", motion.mergeAmount)
        shader.setFloatUniform("smoothness", motion.smoothness)
        shader.setFloatUniform("lean", motion.leanX, motion.leanY)
        shader.setFloatUniform("press", motion.pressAmount)
        shader.setFloatUniform("lightPosition", motion.lightX, motion.lightY)
        shader.setFloatUniform("glow", motion.glowAmount)
        shader.setFloatUniform("density", density)
        shader.setFloatUniform("innerHeight", innerHeight)
        shader.setFloatUniform("innerAmount", innerAmount)
        shader.setFloatUniform("outerHeight", outerHeight)
        shader.setFloatUniform("outerAmount", outerAmount)

        val parameters = floatArrayOf(
            blur, innerHeight, innerAmount, density,
            first.left, first.top, first.right, first.bottom, motion.primaryRadius,
            second.left, second.top, second.right, second.bottom, motion.secondaryRadius,
            motion.mergeAmount, motion.smoothness, motion.leanX, motion.leanY,
            motion.pressAmount, motion.lightX, motion.lightY, motion.glowAmount
        )
        if (cachedEffect == null || !lastEffectParameters.contentEquals(parameters)) {
            // Rebind the effect when any optical/shape parameter changes.
            // Scrolling alone can still reuse it, while the backing display
            // list is recorded again with the fresh content.
            val optical = RenderEffect.createRuntimeShaderEffect(shader, "content")
            cachedEffect = if (blur > 0f) {
                RenderEffect.createChainEffect(optical,
                    RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
            } else optical
            lastEffectParameters = parameters
        }
        node.setPosition(0, 0, w, h)
        node.setRenderEffect(cachedEffect)
        val recording = node.beginRecording(w, h)
        try {
            recording.translate(-x.toFloat(), -y.toFloat())
            recording.concat(sourceMatrix)
            source.draw(recording)
        } finally {
            node.endRecording()
        }
        val save = canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.drawRenderNode(node)
        canvas.restoreToCount(save)
    }

    fun release() {
        node.discardDisplayList()
    }
}
