package com.norwood.meshify.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class ObjTemplate {
   private static final Map<String, ObjTemplate> CACHE = new ConcurrentHashMap<>();
   private final Map<String, List<ObjTemplate.Face>> groups = new LinkedHashMap<>();

   public static ObjTemplate getOrLoad(String name) throws IOException {
      ObjTemplate cached = CACHE.get(name);
      if (cached != null) {
         return cached;
      }

      ObjTemplate loaded = load(name);
      CACHE.put(name, loaded);
      return loaded;
   }

   private static ObjTemplate load(String name) throws IOException {
      InputStream stream = ObjTemplate.class.getResourceAsStream("/assets/meshify/models/" + name);
      if (stream == null) {
         throw new IOException("template not found: " + name);
      }

      ObjTemplate template = new ObjTemplate();
      List<float[]> positions = new ArrayList<>();
      List<float[]> texcoords = new ArrayList<>();
      String group = "default";
      String material = "default";

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
         String line;
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
               continue;
            }

            String[] tokens = line.split("\\s+");
            switch (tokens[0]) {
               case "o", "g" -> group = tokens.length > 1 ? tokens[1] : "default";
               case "usemtl" -> material = tokens.length > 1 ? tokens[1] : "default";
               case "v" -> positions.add(new float[]{Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), Float.parseFloat(tokens[3])});
               case "vt" -> texcoords.add(new float[]{Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2])});
               case "f" -> template.addFace(group, material, tokens, positions, texcoords);
               default -> {
               }
            }
         }
      }

      return template;
   }

   /** Splits a polygon into a fan of quads; an odd corner count leaves the last one a triangle with a doubled corner. */
   private void addFace(String group, String material, String[] tokens, List<float[]> positions, List<float[]> texcoords) {
      int cornerCount = tokens.length - 1;
      float[][] corners = new float[cornerCount][];
      float[][] uv = new float[cornerCount][];

      for (int k = 0; k < cornerCount; k++) {
         // v/vt/vn, 1-based, or negative to count back from the latest.
         String[] refs = tokens[k + 1].split("/");
         int v = Integer.parseInt(refs[0]);
         corners[k] = positions.get(v > 0 ? v - 1 : positions.size() + v);
         uv[k] = new float[]{0.0F, 0.0F};
         if (refs.length > 1 && !refs[1].isEmpty()) {
            int vt = Integer.parseInt(refs[1]);
            float[] texcoord = texcoords.get(vt > 0 ? vt - 1 : texcoords.size() + vt);
            // OBJ's V runs up from the bottom; the game's textures run down from the top.
            uv[k] = new float[]{texcoord[0], 1.0F - texcoord[1]};
         }
      }

      List<ObjTemplate.Face> faces = this.groups.computeIfAbsent(group, k -> new ArrayList<>());

      for (int a = 1; a + 1 < cornerCount; a += 2) {
         int b = a + 1;
         int c = a + 2 < cornerCount ? a + 2 : b;
         faces.add(new ObjTemplate.Face(new float[][]{corners[0], corners[a], corners[b], corners[c]}, new float[][]{uv[0], uv[a], uv[b], uv[c]}, material));
      }
   }

   /** Emits {@code group} (or every group when null), turned {@code yawDegrees} about the block's centre; faces the resolver maps to null are dropped. */
   public void emit(MeshBuilder mesh, String group, BiFunction<String, float[], TextureKey> resolver, double blockX, double blockY, double blockZ, double yawDegrees) {
      double yaw = Math.toRadians(yawDegrees);
      double cos = Math.cos(yaw);
      double sin = Math.sin(yaw);

      for (Entry<String, List<ObjTemplate.Face>> entry : this.groups.entrySet()) {
         if (group != null && !entry.getKey().equals(group)) {
            continue;
         }

         for (ObjTemplate.Face face : entry.getValue()) {
            float[] normal = MeshBuilder.faceNormal(face.positions());
            TextureKey texture = resolver.apply(face.material(), normal);
            if (texture == null) {
               continue;
            }

            float[][] positions = new float[4][3];

            for (int k = 0; k < 4; k++) {
               double x = face.positions()[k][0];
               double y = face.positions()[k][1];
               double z = face.positions()[k][2];
               positions[k][0] = (float)(blockX + 0.5 + x * cos - z * sin);
               positions[k][1] = (float)(blockY + 0.5 + y);
               positions[k][2] = (float)(blockZ + 0.5 + x * sin + z * cos);
            }

            float[][] uv = new float[4][2];

            for (int k = 0; k < 4; k++) {
               uv[k][0] = face.uv()[k][0];
               uv[k][1] = face.uv()[k][1];
            }

            float normalX = (float)(normal[0] * cos - normal[2] * sin);
            float normalZ = (float)(normal[0] * sin + normal[2] * cos);
            mesh.addQuad(texture, positions, uv, new float[]{normalX, normal[1], normalZ});
         }
      }
   }

   private record Face(float[][] positions, float[][] uv, String material) {
   }
}
