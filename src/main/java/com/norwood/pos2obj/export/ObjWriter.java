package com.norwood.pos2obj.export;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

public final class ObjWriter {
   private ObjWriter() {
   }

   public static void write(MinecraftClient mc, Path outDir, ExportOptions opts, MeshBuilder mesh, List<String> warnings) throws IOException {
      Files.createDirectories(outDir);

      try (TextureStore texturestore = new TextureStore(mc, opts.texScale())) {
         for (TexKey texkey : mesh.keys().values()) {
            texturestore.get(texkey, warnings);
         }

         Map<String, float[]> map = new LinkedHashMap<>();
         if (!opts.mergeAtlas()) {
            Path path = outDir.resolve("textures");
            Files.createDirectories(path);

            for (Entry<String, NativeImage> entry1 : texturestore.images().entrySet()) {
               entry1.getValue().writeTo(path.resolve(entry1.getKey() + ".png"));
               map.put(entry1.getKey(), new float[]{1.0F, 1.0F, 0.0F, 0.0F});
            }
         } else {
            TextureStore.Atlas texturestore$atlas = texturestore.packAtlas();

            for (Entry<String, TextureStore.AtlasEntry> entry : texturestore$atlas.entries().entrySet()) {
               TextureStore.AtlasEntry texturestore$atlasentry = entry.getValue();
               map.put(
                  entry.getKey(),
                  new float[]{
                     (float)texturestore$atlasentry.w() / texturestore$atlas.width(),
                     (float)texturestore$atlasentry.h() / texturestore$atlas.height(),
                     (float)texturestore$atlasentry.x() / texturestore$atlas.width(),
                     (float)texturestore$atlasentry.y() / texturestore$atlas.height()
                  }
               );
            }

            texturestore$atlas.image().writeTo(outDir.resolve("atlas.png"));
         }

         writeObj(outDir, opts, mesh, map);
         writeMtl(outDir, opts, mesh);
      }
   }

   private static void writeObj(Path outDir, ExportOptions opts, MeshBuilder mesh, Map<String, float[]> uvXform) throws IOException {
      LinkedHashMap<String, Integer> linkedhashmap = new LinkedHashMap<>();
      LinkedHashMap<String, Integer> linkedhashmap1 = new LinkedHashMap<>();
      LinkedHashMap<String, Integer> linkedhashmap2 = new LinkedHashMap<>();
      StringBuilder stringbuilder = new StringBuilder();

      for (Entry<String, List<MeshBuilder.Quad>> entry : mesh.quads().entrySet()) {
         String s = entry.getKey();
         float[] afloat = uvXform.getOrDefault(s, new float[]{1.0F, 1.0F, 0.0F, 0.0F});
         stringbuilder.append("g ").append(s).append('\n');
         stringbuilder.append("usemtl ").append(opts.mergeAtlas() ? "atlas" : s).append('\n');

         for (MeshBuilder.Quad meshbuilder$quad : entry.getValue()) {
            int[] aint = new int[4];
            int[] aint1 = new int[4];
            String s1 = String.format(Locale.ROOT, "%.4f %.4f %.4f", meshbuilder$quad.normal()[0], meshbuilder$quad.normal()[1], meshbuilder$quad.normal()[2]);
            int i = linkedhashmap2.computeIfAbsent(s1, kx -> linkedhashmap2.size() + 1);

            for (int j = 0; j < 4; j++) {
               String s2 = String.format(
                  Locale.ROOT, "%.5f %.5f %.5f", meshbuilder$quad.pos()[j][0], meshbuilder$quad.pos()[j][1], meshbuilder$quad.pos()[j][2]
               );
               aint[j] = linkedhashmap.computeIfAbsent(s2, kx -> linkedhashmap.size() + 1);
               float f = afloat[2] + meshbuilder$quad.uv()[j][0] * afloat[0];
               float f1 = afloat[3] + meshbuilder$quad.uv()[j][1] * afloat[1];
               String s3 = String.format(Locale.ROOT, "%.6f %.6f", f, 1.0F - f1);
               aint1[j] = linkedhashmap1.computeIfAbsent(s3, kx -> linkedhashmap1.size() + 1);
            }

            stringbuilder.append('f');

            for (int k = 0; k < 4; k++) {
               stringbuilder.append(' ').append(aint[k]).append('/').append(aint1[k]).append('/').append(i);
            }

            stringbuilder.append('\n');
         }
      }

      Path path = outDir.resolve(opts.name() + ".obj");

      try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
         writer.write("# Exported by pos2obj (Fabric client mod)\n");
         writer.write("# 1 unit = 1 block\n");
         writer.write("mtllib " + opts.name() + ".mtl\n");
         writer.write("o " + opts.name() + "\n");

         for (String s4 : linkedhashmap.keySet()) {
            writer.write("v " + s4 + "\n");
         }

         for (String s5 : linkedhashmap1.keySet()) {
            writer.write("vt " + s5 + "\n");
         }

         for (String s6 : linkedhashmap2.keySet()) {
            writer.write("vn " + s6 + "\n");
         }

         writer.write(stringbuilder.toString());
      }
   }

   private static void writeMtl(Path outDir, ExportOptions opts, MeshBuilder mesh) throws IOException {
      Path path = outDir.resolve(opts.name() + ".mtl");

      try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
         if (opts.mergeAtlas()) {
            writer.write(material("atlas", "atlas.png"));
         } else {
            for (String s : mesh.keys().keySet()) {
               writer.write(material(s, "textures/" + s + ".png"));
            }
         }
      }
   }

   private static String material(String name, String texPath) {
      return "newmtl " + name + "\nKd 1.000 1.000 1.000\nKs 0.000 0.000 0.000\nd 1.0\nillum 2\nmap_Kd " + texPath + "\nmap_d " + texPath + "\n\n";
   }
}
