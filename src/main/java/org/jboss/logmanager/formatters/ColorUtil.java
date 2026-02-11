/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.formatters;

/**
 * This is a throwaway temp class.
 */
final class ColorUtil {
    private ColorUtil() {
    }

    /**
     * Start a foreground color using HSL (hue-saturation-lightness) color coordinates.
     *
     * @param target    the target string builder (must not be {@code null})
     * @param trueColor {@code true} to use truecolor RGB, or {@code false} to use the standard 256-color palette
     * @param hue       the hue phase angle, in degrees
     * @param sat       the saturation as a value between {@code 0.0} and {@code 1.0} (inclusive)
     * @param lit       the lightness as a value between {@code 0.0} and {@code 1.0} (inclusive)
     * @param darken    {@code true} to invert the lightness value, or {@code false} to leave it as-is
     * @return the string builder that was passed in (not null)
     * @see #startColor(StringBuilder, int, boolean, float, float, float)
     */
    static StringBuilder startFgColor(StringBuilder target, boolean trueColor, float hue, float sat, float lit,
            boolean darken) {
        return startColor(target, 38, trueColor, hue, sat, darken ? 1f - (lit * .8f) : lit);
    }

    static StringBuilder startBgColor(StringBuilder target, boolean trueColor, int r, int g, int b) {
        return startColor(target, 48, trueColor, r, g, b);
    }

    /**
     * Start a color using HSL (hue-saturation-lightness) color coordinates.
     * This method performs a mathematical transformation from HSV to RGB and delegates to
     * {@link #startColor(StringBuilder, int, boolean, int, int, int)}.
     * <p>
     * The {@code hue} parameter is a color phase angle, with pure red at 0°, green at 120°, blue at 240°, returning
     * to red again at 360° where the cycle repeats infinitely.
     * <p>
     * The {@code sat} parameter defines the color saturation and is clipped between the values of {@code 0.0} and
     * {@code 1.0} (inclusive).
     * A saturation of {@code 1.0} (or 100%) represents a pure color.
     * Decreasing saturation towards 0 decreases the hue of the result, where {@code 0.0} (or 0%) will result in
     * black, white, or a shade of gray.
     * <p>
     * The {@code lit} parameter specifies the lightness of the color and is clipped between the values of {@code 0.0}
     * and {@code 1.0} (inclusive).
     * A lightness of {@code 0.0} (or 0%) results in black, and a lightness of {@code 1.0} (or 100%) results in white.
     * Values in between results in tints or shades of the final color, with {@code 0.5} (or 50%) representing a "pure"
     * color.
     *
     * @param target    the target string builder (must not be {@code null})
     * @param mode      the ANSI-style rendition mode
     * @param trueColor {@code true} to use truecolor RGB, or {@code false} to use the standard 256-color palette
     * @param hue       the hue phase angle, in degrees
     * @param sat       the saturation as a value between {@code 0.0} and {@code 1.0} (inclusive)
     * @param lit       the lightness as a value between {@code 0.0} and {@code 1.0} (inclusive)
     * @return the string builder that was passed in (not null)
     */
    static StringBuilder startColor(StringBuilder target, int mode, boolean trueColor, float hue, float sat, float lit) {
        // lock hue into range (hue is periodic) (color phase angle in degrees)
        hue = hue / 360;
        hue = hue - (float) Math.floor(hue);
        // lock sat and lite into range via clipping (%)
        sat = Math.max(0f, Math.min(1f, sat));
        lit = Math.max(0f, Math.min(1f, lit));

        float c = (1 - Math.abs(2 * lit - 1)) * sat;
        float x = c * (1 - Math.abs((hue * 6f) % 2 - 1));
        float m = lit - c / 2;
        float r = m, g = m, b = m;
        switch ((int) (hue * 6f)) {
            case 0 -> {
                r += c;
                g += x;
            }
            case 1 -> {
                r += x;
                g += c;
            }
            case 2 -> {
                g += c;
                b += x;
            }
            case 3 -> {
                g += x;
                b += c;
            }
            case 4 -> {
                r += x;
                b += c;
            }
            case 5 -> {
                r += c;
                b += x;
            }
        }
        return startColor(target, mode, trueColor, (int) (r * 255), (int) (g * 255), (int) (b * 255));
    }

    /**
     * Start a color using ANSI-style rendition escape codes.
     * <p>
     * In truecolor mode, the RGB values are passed directly to the terminal.
     * <p>
     * In 256 color mode, the RGB values are interpolated as follows:
     * <ul>
     * <li>Scale the original R, G, and B values to an integer between 0 and 5 (inclusive), rounding half-up</li>
     * <li>If the scaled R, G, and B values are all equal, choose a gray shade as follows:
     * <ul>
     * <li>Re-scale the sum of the original R, G, and B values to an integer between 0 and 25 (inclusive), rounding half-up</li>
     * <li>If the resultant value is 0, choose ANSI color 16 (black)</li>
     * <li>If the resultant value is 25, choose ANSI color 231 (white)</li>
     * <li>Otherwise, linearly map colors 1-24 to the ANSI gray shades at 232 (darkest gray) through 255 (lightest gray)</li>
     * </ul>
     * </li>
     * <li>Otherwise, choose a color from the RGB space by this formula: {@code color = r * 36 + g * 6 + b}</li>
     * </ul>
     * <p>
     * <b>A note about 256-color mode</b>:
     * there are an additional 4 gray shades available in the 6-by-6-by-6 color space of the 256-color palette.
     * They would theoretically have lightnesses of 20%, 40%, 60%, and 80%.
     * The lightness of the gray-shades section, on the other hand, would be expected to increase in increments of 4%
     * based on having 25 shades available (if you include black and white from the RGB space).
     * Assuming that this expectation is valid, every fifth shade in the grayscale palette should match one of the
     * grayscale colors in the RGB palette.
     * <p>
     * In practice this is not the case.
     * It appears that the original {@code xterm} source code uses a somewhat nonlinear mapping of RGB values,
     * jumping from 0 to around 37% when going from 0 to 1, and increasing by around 15 or 16% thereafter.
     * Likewise, the grayscale values are also nonlinear, with a typical difference between black
     * and darkest gray of around 3.1%, and a difference between lightest gray and white of around 6.7%,
     * with the remaining increments between gray shades being around 3.9%.
     * <p>
     * This method ignores these aberrations and assumes that both of these scales are fully linear.
     * The result is that on some terminals, some RBG colors might appear ever so slightly lighter in
     * 256-color mode than expected, even accounting for the lack of color granularity; likewise,
     * some grayscale colors might appear slightly lighter or darker than expected.
     * This is a feature, not a bug.
     * It is expected that users who care greatly about color fidelity will use truecolor-capable terminals to
     * begin with.
     *
     * @param target    the target string builder (must not be {@code null})
     * @param mode      the ANSI-style rendition mode
     * @param trueColor {@code true} to use truecolor RGB, or {@code false} to use the standard 256-color palette
     * @param r         the red value, as an integer between {@code 0} and {@code 255} (inclusive)
     * @param g         the green value, as an integer between {@code 0} and {@code 255} (inclusive)
     * @param b         the blue value, as an integer between {@code 0} and {@code 255} (inclusive)
     * @return the string builder that was passed in (not null)
     */
    static StringBuilder startColor(StringBuilder target, int mode, boolean trueColor, int r, int g, int b) {
        if (trueColor) {
            return target.appendCodePoint(27).append('[').append(mode).append(';').append(2).append(';').append(clip(r))
                    .append(';').append(clip(g)).append(';').append(clip(b)).append('m');
        } else {
            // try RGB
            int ar = (5 * clip(r) + 127) / 255;
            int ag = (5 * clip(g) + 127) / 255;
            int ab = (5 * clip(b) + 127) / 255;
            int col;
            if (ar == ag && ar == ab) {
                // do a more accurate grayscale calculation instead
                col = ((clip(r) + clip(g) + clip(b)) * 25 + 382) / 765;
                switch (col) {
                    case 0 -> col = 16;
                    case 25 -> col = 231;
                    default -> col = 231 + col;
                }
            } else {
                col = 16 + 36 * ar + 6 * ag + ab;
            }
            return target.appendCodePoint(27).append('[').append(mode).append(';').append('5').append(';').append(col)
                    .append('m');
        }
    }

    private static int clip(int color) {
        return Math.min(Math.max(0, color), 255);
    }

    static StringBuilder endFgColor(StringBuilder target) {
        return endColor(target, 39);
    }

    static StringBuilder endBgColor(StringBuilder target) {
        return endColor(target, 49);
    }

    static StringBuilder endColor(StringBuilder target, int mode) {
        return target.appendCodePoint(27).append('[').append(mode).append('m');
    }
}
