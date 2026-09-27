package com.norwood.meshify.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes a self-contained .glb for web viewers: unlit, with baked face shading, one atlas and at most three draw calls. */
public final class GlbWriter {
   public static final int OPAQUE = 0;
   public static final int MASK = 1;
   public static final int BLEND = 2;

   private static final String[] ALPHA_MODE = {"OPAQUE", "MASK", "BLEND"};
   private static final float[] UV_IDENTITY = {1.0F, 1.0F, 0.0F, 0.0F};

   private static final int NEAREST = 9728;
   private static final int CLAMP_TO_EDGE = 33071;
   private static final int ARRAY_BUFFER = 34962;
   private static final int ELEMENT_ARRAY_BUFFER = 34963;
   private static final int UNSIGNED_BYTE = 5121;
   private static final int UNSIGNED_SHORT = 5123;
   private static final int UNSIGNED_INT = 5125;
   private static final int FLOAT = 5126;

   private GlbWriter() {
   }

   /** {@code uvXform} is {scaleU, scaleV, offsetU, offsetV} and {@code alphaClass} is OPAQUE/MASK/BLEND, per texture key. */
   public static void write(
      Path outDir, String name, byte[] atlasPng, MeshBuilder mesh, Map<String, float[]> uvXform, Map<String, Integer> alphaClass
   ) throws IOException {
      Bucket[] buckets = {new Bucket(), new Bucket(), new Bucket()};

      for (Map.Entry<String, List<MeshBuilder.Quad>> entry : mesh.quads().entrySet()) {
         float[] t = uvXform.getOrDefault(entry.getKey(), UV_IDENTITY);
         Bucket bucket = buckets[clampMode(alphaClass.getOrDefault(entry.getKey(), OPAQUE))];
         // Emissive faces skip the directional shading, or an unlit export makes them look like dead stone.
         boolean emissive = mesh.emissiveKeys().contains(entry.getKey());

         for (MeshBuilder.Quad quad : entry.getValue()) {
            byte shade = emissive ? (byte)255 : shadeByte(quad.normal());
            int[] vi = new int[4];

            for (int i = 0; i < 4; i++) {
               float[] p = quad.pos()[i];
               // glTF's UV origin is top-left like the atlas, so unlike the OBJ path there is no V flip.
               float u = t[2] + quad.uv()[i][0] * t[0];
               float v = t[3] + quad.uv()[i][1] * t[1];
               vi[i] = bucket.vertex(p[0], p[1], p[2], u, v, shade);
            }

            bucket.triangle(vi[0], vi[1], vi[2]);
            bucket.triangle(vi[0], vi[2], vi[3]);
         }
      }

      Files.write(outDir.resolve(name + ".glb"), build(buckets, atlasPng));
   }

   /** Minecraft's face shading: top 1.0, north/south 0.8, east/west 0.6, bottom 0.5. */
   static byte shadeByte(float[] normal) {
      float ax = Math.abs(normal[0]);
      float ay = Math.abs(normal[1]);
      float az = Math.abs(normal[2]);
      float shade;
      if (ay >= ax && ay >= az) {
         shade = normal[1] >= 0.0F ? 1.0F : 0.5F;
      } else if (az >= ax) {
         shade = 0.8F;
      } else {
         shade = 0.6F;
      }

      return (byte)Math.round(srgbToLinear(shade) * 255.0F);
   }

   /** The game shades in sRGB but glTF's COLOR_0 is linear, so the factor is stored linearised. */
   private static float srgbToLinear(float c) {
      return c <= 0.04045F ? c / 12.92F : (float)Math.pow((c + 0.055F) / 1.055F, 2.4);
   }

   private static int clampMode(int mode) {
      return mode < OPAQUE || mode > BLEND ? OPAQUE : mode;
   }

   private static byte[] build(Bucket[] buckets, byte[] atlasPng) throws IOException {
      ByteArrayOutputStream bin = new ByteArrayOutputStream();
      List<String> bufferViews = new ArrayList<>();
      List<String> accessors = new ArrayList<>();
      List<String> primitives = new ArrayList<>();
      List<String> materials = new ArrayList<>();

      for (int mode = 0; mode < buckets.length; mode++) {
         Bucket bucket = buckets[mode];
         if (bucket.vertexCount() == 0) {
            continue;
         }

         int n = bucket.vertexCount();
         int position = accessor(
            bin, bufferViews, accessors, bucket.positions(n), ARRAY_BUFFER, FLOAT, "VEC3", n, false, bucket.minMax()
         );
         int texcoord = accessor(bin, bufferViews, accessors, bucket.texcoords(n), ARRAY_BUFFER, FLOAT, "VEC2", n, false, null);
         int color = accessor(bin, bufferViews, accessors, bucket.colors(n), ARRAY_BUFFER, UNSIGNED_BYTE, "VEC4", n, true, null);

         // uint16 indices while the bucket fits, which halves the index block on most exports.
         boolean small = n <= 65535;
         int indices = accessor(
            bin,
            bufferViews,
            accessors,
            bucket.indices(small),
            ELEMENT_ARRAY_BUFFER,
            small ? UNSIGNED_SHORT : UNSIGNED_INT,
            "SCALAR",
            bucket.indexCount(),
            false,
            null
         );

         int material = materials.size();
         materials.add(material(mode));
         primitives.add(
            "{\"attributes\":{\"POSITION\":" + position + ",\"TEXCOORD_0\":" + texcoord + ",\"COLOR_0\":" + color + "},\"indices\":" + indices
               + ",\"material\":" + material + "}"
         );
      }

      if (primitives.isEmpty()) {
         throw new IOException("nothing to export: the mesh has no faces");
      }

      int image = bufferView(bin, bufferViews, atlasPng, -1);
      String json = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"meshify\"},"
         + "\"extensionsUsed\":[\"KHR_materials_unlit\"],"
         + "\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"mesh\":0}],"
         + "\"meshes\":[{\"primitives\":[" + String.join(",", primitives) + "]}],"
         + "\"materials\":[" + String.join(",", materials) + "],"
         + "\"textures\":[{\"sampler\":0,\"source\":0}],"
         + "\"images\":[{\"bufferView\":" + image + ",\"mimeType\":\"image/png\"}],"
         + "\"samplers\":[{\"magFilter\":" + NEAREST + ",\"minFilter\":" + NEAREST + ",\"wrapS\":" + CLAMP_TO_EDGE + ",\"wrapT\":" + CLAMP_TO_EDGE + "}],"
         + "\"accessors\":[" + String.join(",", accessors) + "],"
         + "\"bufferViews\":[" + String.join(",", bufferViews) + "],"
         + "\"buffers\":[{\"byteLength\":" + bin.size() + "}]}";

      return container(json.getBytes(StandardCharsets.UTF_8), bin.toByteArray());
   }

   private static String material(int mode) {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0},\"metallicFactor\":0,\"roughnessFactor\":1},");
      sb.append("\"extensions\":{\"KHR_materials_unlit\":{}},");
      sb.append("\"alphaMode\":\"").append(ALPHA_MODE[mode]).append('"');
      if (mode == MASK) {
         sb.append(",\"alphaCutoff\":0.5");
      }

      // Foliage and glass are two-sided in-game; single-sided faces would punch holes in the build.
      if (mode != OPAQUE) {
         sb.append(",\"doubleSided\":true");
      }

      return sb.append(",\"name\":\"").append(ALPHA_MODE[mode].toLowerCase(java.util.Locale.ROOT)).append("\"}").toString();
   }

   private static int accessor(
      ByteArrayOutputStream bin,
      List<String> bufferViews,
      List<String> accessors,
      byte[] data,
      int target,
      int componentType,
      String type,
      int count,
      boolean normalized,
      float[][] minMax
   ) {
      int view = bufferView(bin, bufferViews, data, target);
      StringBuilder sb = new StringBuilder();
      sb.append("{\"bufferView\":").append(view).append(",\"componentType\":").append(componentType);
      sb.append(",\"count\":").append(count).append(",\"type\":\"").append(type).append('"');
      if (normalized) {
         sb.append(",\"normalized\":true");
      }

      if (minMax != null) {
         sb.append(",\"min\":[").append(join(minMax[0])).append("],\"max\":[").append(join(minMax[1])).append(']');
      }

      accessors.add(sb.append('}').toString());
      return accessors.size() - 1;
   }

   private static int bufferView(ByteArrayOutputStream bin, List<String> bufferViews, byte[] data, int target) {
      pad(bin, 4);
      int offset = bin.size();
      bin.writeBytes(data);
      String s = "{\"buffer\":0,\"byteOffset\":" + offset + ",\"byteLength\":" + data.length + (target > 0 ? ",\"target\":" + target : "") + "}";
      bufferViews.add(s);
      return bufferViews.size() - 1;
   }

   private static byte[] container(byte[] json, byte[] bin) {
      int jsonPad = (4 - json.length % 4) % 4;
      int binPad = (4 - bin.length % 4) % 4;
      int total = 12 + 8 + json.length + jsonPad + 8 + bin.length + binPad;
      ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
      buf.putInt(0x46546C67); // "glTF"
      buf.putInt(2);
      buf.putInt(total);

      buf.putInt(json.length + jsonPad);
      buf.putInt(0x4E4F534A); // "JSON"
      buf.put(json);
      for (int i = 0; i < jsonPad; i++) {
         buf.put((byte)0x20); // the JSON chunk pads with spaces
      }

      buf.putInt(bin.length + binPad);
      buf.putInt(0x004E4942); // "BIN\0"
      buf.put(bin);
      for (int i = 0; i < binPad; i++) {
         buf.put((byte)0);
      }

      return buf.array();
   }

   private static void pad(ByteArrayOutputStream out, int align) {
      for (int i = (align - out.size() % align) % align; i > 0; i--) {
         out.write(0);
      }
   }

   private static String join(float[] v) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < v.length; i++) {
         if (i > 0) {
            sb.append(',');
         }

         sb.append(String.format(java.util.Locale.ROOT, "%.6f", v[i]));
      }

      return sb.toString();
   }

   /** One alpha mode's worth of geometry: deduplicated vertices plus a triangle index list. */
   private static final class Bucket {
      private final Map<Vertex, Integer> lookup = new HashMap<>();
      private final List<Vertex> vertices = new ArrayList<>();
      private int[] indices = new int[1024];
      private int indexCount;
      private final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
      private final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

      int vertex(float x, float y, float z, float u, float v, byte shade) {
         Vertex key = new Vertex(x, y, z, u, v, shade);
         Integer existing = this.lookup.get(key);
         if (existing != null) {
            return existing;
         }

         int index = this.vertices.size();
         this.vertices.add(key);
         this.lookup.put(key, index);
         this.min[0] = Math.min(this.min[0], x);
         this.min[1] = Math.min(this.min[1], y);
         this.min[2] = Math.min(this.min[2], z);
         this.max[0] = Math.max(this.max[0], x);
         this.max[1] = Math.max(this.max[1], y);
         this.max[2] = Math.max(this.max[2], z);
         return index;
      }

      void triangle(int a, int b, int c) {
         if (this.indexCount + 3 > this.indices.length) {
            int[] grown = new int[this.indices.length * 2];
            System.arraycopy(this.indices, 0, grown, 0, this.indexCount);
            this.indices = grown;
         }

         this.indices[this.indexCount++] = a;
         this.indices[this.indexCount++] = b;
         this.indices[this.indexCount++] = c;
      }

      int vertexCount() {
         return this.vertices.size();
      }

      int indexCount() {
         return this.indexCount;
      }

      float[][] minMax() {
         return new float[][]{this.min, this.max};
      }

      byte[] positions(int n) {
         ByteBuffer buf = ByteBuffer.allocate(n * 12).order(ByteOrder.LITTLE_ENDIAN);
         for (Vertex vertex : this.vertices) {
            buf.putFloat(vertex.x()).putFloat(vertex.y()).putFloat(vertex.z());
         }

         return buf.array();
      }

      byte[] texcoords(int n) {
         ByteBuffer buf = ByteBuffer.allocate(n * 8).order(ByteOrder.LITTLE_ENDIAN);
         for (Vertex vertex : this.vertices) {
            buf.putFloat(vertex.u()).putFloat(vertex.v());
         }

         return buf.array();
      }

      byte[] colors(int n) {
         // Grey, so r=g=b; VEC4 because glTF wants attribute elements 4-byte aligned.
         ByteBuffer buf = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN);
         for (Vertex vertex : this.vertices) {
            buf.put(vertex.shade()).put(vertex.shade()).put(vertex.shade()).put((byte)255);
         }

         return buf.array();
      }

      byte[] indices(boolean small) {
         ByteBuffer buf = ByteBuffer.allocate(this.indexCount * (small ? 2 : 4)).order(ByteOrder.LITTLE_ENDIAN);
         for (int i = 0; i < this.indexCount; i++) {
            if (small) {
               buf.putShort((short)this.indices[i]);
            } else {
               buf.putInt(this.indices[i]);
            }
         }

         return buf.array();
      }
   }

   private record Vertex(float x, float y, float z, float u, float v, byte shade) {
   }

   /** Run with {@code gradlew glbCheck}. */
   public static void main(String[] args) throws IOException {
      // Linearised shade factors: 1.0 / 0.8 / 0.6 / 0.5 through the sRGB transfer function.
      check(shadeByte(new float[]{0.0F, 1.0F, 0.0F}) == (byte)255, "top face is full bright");
      check(shadeByte(new float[]{0.0F, 0.0F, 1.0F}) == (byte)154, "south face is 0.8 linearised");
      check(shadeByte(new float[]{-1.0F, 0.0F, 0.0F}) == (byte)81, "west face is 0.6 linearised");
      check(shadeByte(new float[]{0.0F, -1.0F, 0.0F}) == (byte)55, "bottom face is 0.5 linearised");

      float[][] afloat = {{0.0F, 1.0F, 0.0F}, {0.0F, 1.0F, 1.0F}, {1.0F, 1.0F, 1.0F}, {1.0F, 1.0F, 0.0F}};
      float[][] afloat1 = {{0.0F, 0.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}};
      float[] afloat2 = {0.0F, 1.0F, 0.0F};
      TexKey texkey = TexKey.generated("stone", mc -> null);
      TexKey texkey1 = TexKey.generated("leaves", mc -> null);
      MeshBuilder meshbuilder = new MeshBuilder();
      meshbuilder.addQuad(texkey, afloat, afloat1, afloat2);
      meshbuilder.addQuad(texkey, afloat, afloat1, afloat2);
      meshbuilder.addQuad(texkey1, afloat, afloat1, afloat2);

      Path path = Files.createTempDirectory("meshify-check");
      write(
         path,
         "check",
         new byte[]{1, 2, 3},
         meshbuilder,
         Map.of("stone", UV_IDENTITY, "leaves", UV_IDENTITY),
         Map.of("stone", OPAQUE, "leaves", MASK)
      );

      byte[] abyte = Files.readAllBytes(path.resolve("check.glb"));
      ByteBuffer bytebuffer = ByteBuffer.wrap(abyte).order(ByteOrder.LITTLE_ENDIAN);
      check(bytebuffer.getInt() == 0x46546C67, "glTF magic");
      check(bytebuffer.getInt() == 2, "glTF version 2");
      check(bytebuffer.getInt() == abyte.length, "header length matches the file");

      int i = bytebuffer.getInt();
      check(bytebuffer.getInt() == 0x4E4F534A, "first chunk is JSON");
      check(i % 4 == 0, "JSON chunk is 4-byte aligned");
      byte[] abyte1 = new byte[i];
      bytebuffer.get(abyte1);
      String s = new String(abyte1, StandardCharsets.UTF_8).trim();

      int j = bytebuffer.getInt();
      check(bytebuffer.getInt() == 0x004E4942, "second chunk is BIN");
      check(j % 4 == 0, "BIN chunk is 4-byte aligned");
      check(12 + 8 + i + 8 + j == abyte.length, "chunks fill the file exactly");

      // Two identical quads must dedup to 4 vertices and 12 indices.
      check(s.contains("\"count\":4,\"type\":\"VEC3\""), "opaque quads deduplicated to 4 vertices");
      check(s.contains("\"count\":12,\"type\":\"SCALAR\""), "opaque bucket emitted 12 indices");
      check(s.contains("\"alphaMode\":\"MASK\",\"alphaCutoff\":0.5"), "cutout material");
      check(s.contains("\"alphaMode\":\"OPAQUE\""), "opaque material");
      check(!s.contains("BLEND"), "no empty blend bucket");
      check(s.contains("KHR_materials_unlit"), "unlit extension declared");
      check(s.contains("\"magFilter\":9728"), "nearest sampling");
      check(!s.contains("NORMAL"), "no normal attribute");
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"buffers\":\\[\\{\"byteLength\":(\\d+)").matcher(s);
      check(matcher.find(), "buffer declared");
      int k = Integer.parseInt(matcher.group(1));
      // The BIN chunk is padded up to a 4-byte boundary, so it can exceed the buffer by 1-3 bytes.
      check(k <= j && j - k < 4, "declared buffer length matches the BIN chunk");

      Files.delete(path.resolve("check.glb"));
      Files.delete(path);
      System.out.println("GlbWriter self-check passed");
   }

   private static void check(boolean ok, String what) {
      if (!ok) {
         throw new AssertionError("GlbWriter self-check failed: " + what);
      }
   }
}
