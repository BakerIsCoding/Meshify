package com.norwood.meshify.export;

import com.mojang.blaze3d.platform.NativeImage;
import com.norwood.meshify.export.GlbWriter.AlphaMode;
import com.norwood.meshify.export.TextureStore.UvTransform;
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
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

public final class ObjWriter {
   private ObjWriter() {
   }

   public static void write(Minecraft client, Path outputDir, ExportOptions options, MeshBuilder mesh, List<String> warnings, Consumer<ExportStage> onStage)
      throws IOException {
      onStage.accept(ExportStage.TEXTURES);
      Files.createDirectories(outputDir);

      try (TextureStore textures = new TextureStore(client, options.textureScale())) {
         for (TextureKey key : mesh.textures().values()) {
            textures.getOrLoad(key, warnings);
         }

         // Separate textures keep their own 0..1 UVs, so only the atlas needs transforms.
         Map<String, UvTransform> uvs = Map.of();
         if (!options.mergeAtlas()) {
            Path textureDir = outputDir.resolve("textures");
            Files.createDirectories(textureDir);

            for (Entry<String, NativeImage> texture : textures.images().entrySet()) {
               texture.getValue().writeToFile(textureDir.resolve(texture.getKey() + ".png"));
            }

            warnings.add("no .glb written: it needs a single atlas, so drop the 'separate' option");
         } else {
            TextureStore.Atlas atlas = textures.packAtlas();
            uvs = atlas.uvs();
            Path atlasPath = outputDir.resolve("atlas.png");
            atlas.image().writeToFile(atlasPath);
            onStage.accept(ExportStage.GLB);
            byte[] atlasPng = Files.readAllBytes(atlasPath);
            // The .glb is an extra; when it fails the OBJ is still written.
            try {
               GlbWriter.write(outputDir.resolve(options.name() + ".glb"), mesh, new GlbWriter.Atlas(atlasPng, uvs, alphaModes(textures)));
            } catch (Exception exception) {
               warnings.add("glb export failed: " + exception);
            }
         }

         onStage.accept(ExportStage.OBJ);
         writeObj(outputDir, options, mesh, uvs);
         writeMtl(outputDir, options, mesh);
      }
   }

   private static Map<String, AlphaMode> alphaModes(TextureStore textures) {
      Map<String, AlphaMode> alphaModes = new LinkedHashMap<>();

      for (Entry<String, NativeImage> texture : textures.images().entrySet()) {
         alphaModes.put(texture.getKey(), classifyAlpha(texture.getKey(), texture.getValue()));
      }

      return alphaModes;
   }

   // glTF alpha mode read from the pixels, so the special exporters get classified without extra flags.
   private static AlphaMode classifyAlpha(String id, NativeImage image) {
      // Water only turns translucent through its render layer, which the pixels can't show.
      if (id.startsWith("water")) {
         return AlphaMode.BLEND;
      }

      boolean hasCutout = false;

      for (int y = 0; y < image.getHeight(); y++) {
         for (int x = 0; x < image.getWidth(); x++) {
            int alpha = image.getPixel(x, y) >>> 24;
            if (alpha != 0xFF) {
               if (alpha != 0) {
                  return AlphaMode.BLEND;
               }

               hasCutout = true;
            }
         }
      }

      return hasCutout ? AlphaMode.MASK : AlphaMode.OPAQUE;
   }

   private static void writeObj(Path outputDir, ExportOptions options, MeshBuilder mesh, Map<String, UvTransform> uvs) throws IOException {
      // Each distinct position, UV and normal is written once; faces refer to them by 1-based index.
      Map<String, Integer> positions = new LinkedHashMap<>();
      Map<String, Integer> texcoords = new LinkedHashMap<>();
      Map<String, Integer> normals = new LinkedHashMap<>();
      StringBuilder faces = new StringBuilder();

      for (Entry<String, List<MeshBuilder.Quad>> group : mesh.quads().entrySet()) {
         String key = group.getKey();
         UvTransform uv = uvs.getOrDefault(key, UvTransform.IDENTITY);
         faces.append("g ").append(key).append('\n');
         faces.append("usemtl ").append(options.mergeAtlas() ? "atlas" : key).append('\n');

         for (MeshBuilder.Quad quad : group.getValue()) {
            int normal = indexOrAdd(normals, String.format(Locale.ROOT, "%.4f %.4f %.4f", quad.normal()[0], quad.normal()[1], quad.normal()[2]));
            faces.append('f');

            for (int k = 0; k < 4; k++) {
               float[] corner = quad.positions()[k];
               int position = indexOrAdd(positions, String.format(Locale.ROOT, "%.5f %.5f %.5f", corner[0], corner[1], corner[2]));
               // OBJ's UV origin is bottom-left, the atlas's top-left.
               int texcoord = indexOrAdd(texcoords, String.format(Locale.ROOT, "%.6f %.6f", uv.u(quad.uv()[k][0]), 1.0F - uv.v(quad.uv()[k][1])));
               faces.append(' ').append(position).append('/').append(texcoord).append('/').append(normal);
            }

            faces.append('\n');
         }
      }

      try (Writer writer = Files.newBufferedWriter(outputDir.resolve(options.name() + ".obj"), StandardCharsets.UTF_8)) {
         writer.write("# Exported by meshify (Fabric client mod)\n");
         writer.write("# 1 unit = 1 block\n");
         writer.write("mtllib " + options.name() + ".mtl\n");
         writer.write("o " + options.name() + "\n");

         for (String position : positions.keySet()) {
            writer.write("v " + position + "\n");
         }

         for (String texcoord : texcoords.keySet()) {
            writer.write("vt " + texcoord + "\n");
         }

         for (String normal : normals.keySet()) {
            writer.write("vn " + normal + "\n");
         }

         writer.write(faces.toString());
      }
   }

   // The 1-based OBJ index of value, adding it if it is new.
   private static int indexOrAdd(Map<String, Integer> indices, String value) {
      return indices.computeIfAbsent(value, v -> indices.size() + 1);
   }

   private static void writeMtl(Path outputDir, ExportOptions options, MeshBuilder mesh) throws IOException {
      try (Writer writer = Files.newBufferedWriter(outputDir.resolve(options.name() + ".mtl"), StandardCharsets.UTF_8)) {
         if (options.mergeAtlas()) {
            // One shared material, so it can only be emissive if every texture on it is.
            writer.write(material("atlas", "atlas.png", mesh.emissiveTextureIds().containsAll(mesh.textures().keySet())));
         } else {
            for (String key : mesh.textures().keySet()) {
               writer.write(material(key, "textures/" + key + ".png", mesh.emissiveTextureIds().contains(key)));
            }
         }
      }
   }

   private static String material(String name, String texturePath, boolean emissive) {
      return "newmtl "
         + name
         + "\nKd 1.000 1.000 1.000\nKs 0.000 0.000 0.000\nd 1.0\nillum 2\nmap_Kd "
         + texturePath
         + "\nmap_d "
         + texturePath
         + (emissive ? "\nKe 1.000 1.000 1.000\nmap_Ke " + texturePath : "")
         + "\n\n";
   }
}
