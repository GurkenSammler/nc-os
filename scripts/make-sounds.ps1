# Erzeugt die eigenen Standard-UI-Sounds (sounds\default\sfx_*.wav) rein rechnerisch:
# kurze Sinus-/Rechteck-Toene, Sweeps und etwas Rauschen. Keine fremden Aufnahmen, frei verwendbar.
# Eigene Sounds (z. B. aus einem Spiel) gehoeren nach sounds\custom\ und ersetzen beim Bauen einzelne Dateien.
#   scripts\make-sounds.ps1            (ueberschreibt sounds\default)
param([string]$Out = (Join-Path (Split-Path $PSScriptRoot -Parent) 'sounds\default'))

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $Out | Out-Null

Add-Type -TypeDefinition @'
using System;
using System.IO;

public static class SfxSynth {
    const int Rate = 44100;
    static Random rnd = new Random(2077);

    static double Env(double t, double len, double attack, double decay) {
        if (t < attack) return t / attack;
        return Math.Exp(-(t - attack) / decay) * Math.Max(0, 1 - (t / len) * (t / len) * (t / len));
    }

    // Weiche Rechteckwelle (wenige Obertoene), klingt "digital" ohne zu kratzen.
    static double Sq(double phase) {
        return Math.Sin(phase) + Math.Sin(3 * phase) / 3 + Math.Sin(5 * phase) / 5;
    }

    static double[] Buf(double seconds) { return new double[(int)(seconds * Rate)]; }

    // Ton mit Frequenzverlauf f0 -> f1 (exponentiell) ab start, Lautstaerke amp.
    static void Tone(double[] b, double start, double len, double f0, double f1, double amp, bool square, double decay) {
        double phase = 0;
        int s0 = (int)(start * Rate), n = (int)(len * Rate);
        for (int i = 0; i < n && s0 + i < b.Length; i++) {
            double t = (double)i / Rate;
            double f = f0 * Math.Pow(f1 / f0, t / len);
            phase += 2 * Math.PI * f / Rate;
            double v = square ? Sq(phase) * 0.6 : Math.Sin(phase);
            b[s0 + i] += v * amp * Env(t, len, 0.002, decay);
        }
    }

    static void Noise(double[] b, double start, double len, double amp, double decay, double smooth) {
        int s0 = (int)(start * Rate), n = (int)(len * Rate);
        double lp = 0;
        for (int i = 0; i < n && s0 + i < b.Length; i++) {
            double t = (double)i / Rate;
            lp += (rnd.NextDouble() * 2 - 1 - lp) * smooth;
            b[s0 + i] += lp * amp * Env(t, len, 0.003, decay);
        }
    }

    // Einfaches Echo fuer etwas Raum.
    static void Echo(double[] b, double delay, double gain) {
        int d = (int)(delay * Rate);
        for (int i = b.Length - 1; i >= d; i--) b[i] += b[i - d] * gain;
    }

    // Bitcrush: grobe Stufen, typischer "Terminal"-Klang.
    static void Crush(double[] b, int steps) {
        for (int i = 0; i < b.Length; i++) b[i] = Math.Round(b[i] * steps) / steps;
    }

    public static double[] Make(string name) {
        double[] b;
        switch (name) {
            case "hover":
                b = Buf(0.06); Tone(b, 0, 0.05, 2600, 2400, 0.35, false, 0.012); break;
            case "move":
                b = Buf(0.08); Tone(b, 0, 0.07, 1900, 1500, 0.4, true, 0.018); break;
            case "tab":
                b = Buf(0.14); Tone(b, 0, 0.05, 1500, 1500, 0.35, true, 0.015); Tone(b, 0.055, 0.06, 2250, 2250, 0.35, true, 0.018); break;
            case "press":
                b = Buf(0.16); Noise(b, 0, 0.012, 0.5, 0.004, 0.6); Tone(b, 0.004, 0.12, 900, 1500, 0.45, true, 0.04); break;
            case "enter":
                b = Buf(0.42); Tone(b, 0, 0.26, 600, 1900, 0.4, true, 0.12); Tone(b, 0.2, 0.12, 2400, 2400, 0.25, false, 0.04); Echo(b, 0.07, 0.3); break;
            case "menu_open":
                b = Buf(0.3); Tone(b, 0, 0.1, 880, 880, 0.4, true, 0.05); Tone(b, 0.09, 0.16, 1320, 1320, 0.4, true, 0.07); Crush(b, 24); Echo(b, 0.06, 0.25); break;
            case "menu_close":
                b = Buf(0.3); Tone(b, 0, 0.1, 1320, 1320, 0.4, true, 0.05); Tone(b, 0.09, 0.16, 660, 620, 0.4, true, 0.07); Crush(b, 24); Echo(b, 0.06, 0.25); break;
            case "open":
                b = Buf(0.75); Noise(b, 0, 0.5, 0.25, 0.25, 0.08); Tone(b, 0, 0.5, 180, 1200, 0.35, true, 0.3); Tone(b, 0.48, 0.18, 1800, 1800, 0.3, false, 0.06); Echo(b, 0.09, 0.3); break;
            case "boot":
                b = Buf(1.8);
                Tone(b, 0, 1.6, 70, 140, 0.25, true, 1.2);
                for (int k = 0; k < 26; k++) {
                    double t = 0.05 + k * 0.045;
                    double f = 400 + rnd.Next(0, 16) * 110;
                    Tone(b, t, 0.035, f, f, 0.22, true, 0.012);
                }
                Noise(b, 0.0, 1.2, 0.12, 0.5, 0.3);
                Tone(b, 1.22, 0.5, 660, 660, 0.25, false, 0.25);
                Tone(b, 1.22, 0.5, 990, 990, 0.2, false, 0.25);
                Tone(b, 1.22, 0.5, 1320, 1320, 0.15, false, 0.25);
                Crush(b, 40); Echo(b, 0.11, 0.3); break;
            default: throw new ArgumentException(name);
        }
        // Normalisieren auf -3 dB, Klicks am Ende vermeiden
        double peak = 0;
        foreach (double v in b) peak = Math.Max(peak, Math.Abs(v));
        double g = peak > 0 ? 0.7 / peak : 1;
        int fade = Math.Min(b.Length, Rate / 200);
        for (int i = 0; i < b.Length; i++) {
            b[i] *= g;
            if (i >= b.Length - fade) b[i] *= (double)(b.Length - i) / fade;
        }
        return b;
    }

    public static void Write(string path, double[] b) {
        using (var w = new BinaryWriter(File.Create(path))) {
            int bytes = b.Length * 2;
            w.Write(new[] { 'R', 'I', 'F', 'F' }); w.Write(36 + bytes); w.Write(new[] { 'W', 'A', 'V', 'E' });
            w.Write(new[] { 'f', 'm', 't', ' ' }); w.Write(16); w.Write((short)1); w.Write((short)1);
            w.Write(Rate); w.Write(Rate * 2); w.Write((short)2); w.Write((short)16);
            w.Write(new[] { 'd', 'a', 't', 'a' }); w.Write(bytes);
            foreach (double v in b) w.Write((short)Math.Max(-32767, Math.Min(32767, v * 32767)));
        }
    }
}
'@

foreach ($name in 'boot', 'enter', 'hover', 'menu_close', 'menu_open', 'move', 'open', 'press', 'tab') {
    $file = Join-Path $Out "sfx_$name.wav"
    [SfxSynth]::Write($file, [SfxSynth]::Make($name))
    Write-Host ("{0,-18} {1,7:N0} Bytes" -f "sfx_$name.wav", (Get-Item $file).Length)
}
