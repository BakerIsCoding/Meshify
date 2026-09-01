package com.norwood.pos2obj.export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public final class ObjTemplate {
   private final Map<String, List<ObjTemplate.Face>> groups = new LinkedHashMap<>();
   private static final Map<String, ObjTemplate> CACHE = new ConcurrentHashMap<>();

   public static ObjTemplate get(String name) throws IOException {
      ObjTemplate objtemplate = CACHE.get(name);
      if (objtemplate != null) {
         return objtemplate;
      } else {
         ObjTemplate objtemplate1 = load(name);
         CACHE.put(name, objtemplate1);
         return objtemplate1;
      }
   }

   private static ObjTemplate load(String name) throws IOException {
      InputStream inputstream = ObjTemplate.class.getResourceAsStream("/assets/pos2obj/models/" + name);
      if (inputstream == null) {
         throw new IOException("template not found: " + name);
      } else {
         ObjTemplate objtemplate = new ObjTemplate();
         List<float[]> list = new ArrayList<>();
         List<float[]> list1 = new ArrayList<>();
         String s = "default";
         String s1 = "default";

         String s2;
         try (BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(inputstream, StandardCharsets.UTF_8))) {
            while ((s2 = bufferedreader.readLine()) != null) {
               s2 = s2.trim();
               if (!s2.isEmpty() && !s2.startsWith("#")) {
                  String[] astring = s2.split("\\s+");
                  String s3 = astring[0];
                  switch (s3) {
                     case "o":
                     case "g":
                        s = astring.length > 1 ? astring[1] : "default";
                        break;
                     case "usemtl":
                        s1 = astring.length > 1 ? astring[1] : "default";
                        break;
                     case "v":
                        list.add(new float[]{Float.parseFloat(astring[1]), Float.parseFloat(astring[2]), Float.parseFloat(astring[3])});
                        break;
                     case "vt":
                        list1.add(new float[]{Float.parseFloat(astring[1]), Float.parseFloat(astring[2])});
                        break;
                     case "f":
                        int i = astring.length - 1;
                        float[][] afloat = new float[i][];
                        float[][] afloat1 = new float[i][];

                        for (int j = 0; j < i; j++) {
                           String[] astring1 = astring[j + 1].split("/");
                           int k = Integer.parseInt(astring1[0]);
                           float[] afloat2 = list.get(k > 0 ? k - 1 : list.size() + k);
                           afloat[j] = afloat2;
                           float[] afloat3 = new float[]{0.0F, 0.0F};
                           if (astring1.length > 1 && !astring1[1].isEmpty()) {
                              int l = Integer.parseInt(astring1[1]);
                              float[] afloat4 = list1.get(l > 0 ? l - 1 : list1.size() + l);
                              afloat3 = new float[]{afloat4[0], 1.0F - afloat4[1]};
                           }

                           afloat1[j] = afloat3;
                        }

                        List<ObjTemplate.Face> list2 = objtemplate.groups.computeIfAbsent(s, k -> new ArrayList<>());

                        for (int i1 = 1; i1 + 1 < i; i1 += 2) {
                           int j1 = i1 + 1;
                           int k1 = i1 + 2 < i ? i1 + 2 : j1;
                           list2.add(
                              new ObjTemplate.Face(
                                 new float[][]{afloat[0], afloat[i1], afloat[j1], afloat[k1]},
                                 new float[][]{afloat1[0], afloat1[i1], afloat1[j1], afloat1[k1]},
                                 s1
                              )
                           );
                        }
                  }
               }
            }
         }

         return objtemplate;
      }
   }

   public boolean hasGroup(String name) {
      return this.groups.containsKey(name);
   }

   public void emit(MeshBuilder mesh, String group, BiFunction<String, float[], TexKey> resolver, double bx, double by, double bz, double yawDeg) {
      double d0 = Math.toRadians(yawDeg);
      double d1 = Math.cos(d0);
      double d2 = Math.sin(d0);

      for (Entry<String, List<ObjTemplate.Face>> entry : this.groups.entrySet()) {
         if (group == null || entry.getKey().equals(group)) {
            for (ObjTemplate.Face objtemplate$face : entry.getValue()) {
               float[] afloat = normalOf(objtemplate$face.pos());
               TexKey texkey = resolver.apply(objtemplate$face.material(), afloat);
               if (texkey != null) {
                  float[][] afloat1 = new float[4][3];

                  for (int i = 0; i < 4; i++) {
                     double d3 = objtemplate$face.pos()[i][0];
                     double d4 = objtemplate$face.pos()[i][1];
                     double d5 = objtemplate$face.pos()[i][2];
                     afloat1[i][0] = (float)(bx + 0.5 + d3 * d1 - d5 * d2);
                     afloat1[i][1] = (float)(by + 0.5 + d4);
                     afloat1[i][2] = (float)(bz + 0.5 + d3 * d2 + d5 * d1);
                  }

                  float f = (float)(afloat[0] * d1 - afloat[2] * d2);
                  float f1 = (float)(afloat[0] * d2 + afloat[2] * d1);
                  float[][] afloat2 = new float[4][2];

                  for (int j = 0; j < 4; j++) {
                     afloat2[j][0] = objtemplate$face.uv()[j][0];
                     afloat2[j][1] = objtemplate$face.uv()[j][1];
                  }

                  mesh.addQuad(texkey, afloat1, afloat2, new float[]{f, afloat[1], f1});
               }
            }
         }
      }
   }

   private static float[] normalOf(float[][] p) {
      float f = p[1][0] - p[0][0];
      float f1 = p[1][1] - p[0][1];
      float f2 = p[1][2] - p[0][2];
      float f3 = p[2][0] - p[0][0];
      float f4 = p[2][1] - p[0][1];
      float f5 = p[2][2] - p[0][2];
      float f6 = f1 * f5 - f2 * f4;
      float f7 = f2 * f3 - f * f5;
      float f8 = f * f4 - f1 * f3;
      float f9 = (float)Math.sqrt(f6 * f6 + f7 * f7 + f8 * f8);
      return f9 < 1.0E-8F ? new float[]{0.0F, 1.0F, 0.0F} : new float[]{f6 / f9, f7 / f9, f8 / f9};
   }

   @Override
   public String toString() {
      return "ObjTemplate" + this.groups.keySet().toString().toLowerCase(Locale.ROOT);
   }

   private record Face(float[][] pos, float[][] uv, String material) {
   }
}
