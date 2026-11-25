package com.norwood.meshify.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.norwood.meshify.export.TextureStore.UvTransform;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Writes a self-contained .glb for web viewers: unlit, with baked face shading, one atlas and at most three draw calls.
public final class GlbWriter {
   private static final int NEAREST = 9728;
   private static final int CLAMP_TO_EDGE = 33071;
   private static final int ARRAY_BUFFER = 34962;
   private static final int ELEMENT_ARRAY_BUFFER = 34963;
   private static final int UNSIGNED_BYTE = 5121;
   private static final int UNSIGNED_SHORT = 5123;
   private static final int UNSIGNED_INT = 5125;
   private static final int FLOAT = 5126;
   private static final int GLB_MAGIC = 0x46546C67;
   private static final int GLB_VERSION = 2;
   private static final int JSON_CHUNK = 0x4E4F534A;
   private static final int BIN_CHUNK = 0x004E4942;
   private static final int HEADER_BYTES = 12;
   private static final int CHUNK_HEADER_BYTES = 8;
   // glTF pads the JSON chunk with spaces and the BIN chunk with zeros.
   private static final byte JSON_PADDING = ' ';
   private static final byte BIN_PADDING = 0;
   private static final int MAX_UNSIGNED_SHORT = 0xFFFF;

   private GlbWriter() {
   }

   // The glTF material alpha modes; each gets its own primitive.
   public enum AlphaMode {
      OPAQUE,
      MASK,
      BLEND
   }

   public static void write(Path file, MeshBuilder mesh, GlbWriter.Atlas atlas) throws IOException {
      Map<AlphaMode, Bucket> buckets = new EnumMap<>(AlphaMode.class);

      for (Map.Entry<String, List<MeshBuilder.Quad>> entry : mesh.quads().entrySet()) {
         UvTransform uv = atlas.uvs().getOrDefault(entry.getKey(), UvTransform.IDENTITY);
         Bucket bucket = buckets.computeIfAbsent(atlas.alphaModes().getOrDefault(entry.getKey(), AlphaMode.OPAQUE), mode -> new Bucket());
         // Emissive faces skip the directional shading, or an unlit export makes them look like dead stone.
         boolean emissive = mesh.emissiveTextureIds().contains(entry.getKey());

         for (MeshBuilder.Quad quad : entry.getValue()) {
            byte shade = emissive ? (byte)255 : shadeByte(quad.normal());
            int[] vertexIndices = new int[4];

            for (int i = 0; i < 4; i++) {
               float[] corner = quad.positions()[i];
               // glTF's UV origin is top-left like the atlas, so unlike the OBJ path there is no V flip.
               vertexIndices[i] = bucket.addVertex(corner[0], corner[1], corner[2], uv.u(quad.uv()[i][0]), uv.v(quad.uv()[i][1]), shade);
            }

            bucket.addTriangle(vertexIndices[0], vertexIndices[1], vertexIndices[2]);
            bucket.addTriangle(vertexIndices[0], vertexIndices[2], vertexIndices[3]);
         }
      }

      Files.write(file, build(buckets, atlas.png()));
   }

   // Minecraft's face shading: top 1.0, north/south 0.8, east/west 0.6, bottom 0.5.
   static byte shadeByte(float[] normal) {
      float absX = Math.abs(normal[0]);
      float absY = Math.abs(normal[1]);
      float absZ = Math.abs(normal[2]);
      float shade;
      if (absY >= absX && absY >= absZ) {
         shade = normal[1] >= 0.0F ? 1.0F : 0.5F;
      } else if (absZ >= absX) {
         shade = 0.8F;
      } else {
         shade = 0.6F;
      }

      return (byte)Math.round(srgbToLinear(shade) * 255.0F);
   }

   // The game shades in sRGB but glTF's COLOR_0 is linear, so the factor is stored linearised.
   private static float srgbToLinear(float channel) {
      return channel <= 0.04045F ? channel / 12.92F : (float)Math.pow((channel + 0.055F) / 1.055F, 2.4);
   }

   private static byte[] build(Map<AlphaMode, Bucket> buckets, byte[] atlasPng) throws IOException {
      ByteArrayOutputStream bin = new ByteArrayOutputStream();
      List<String> bufferViews = new ArrayList<>();
      List<String> accessors = new ArrayList<>();
      List<String> primitives = new ArrayList<>();
      List<String> materials = new ArrayList<>();

      for (Map.Entry<AlphaMode, Bucket> entry : buckets.entrySet()) {
         Bucket bucket = entry.getValue();
         if (bucket.vertexCount() == 0) {
            continue;
         }

         int vertexCount = bucket.vertexCount();
         int position = accessor(
            bin, bufferViews, accessors, bucket.positions(), ARRAY_BUFFER, FLOAT, "VEC3", vertexCount, false, bucket.minMax()
         );
         int texcoord = accessor(bin, bufferViews, accessors, bucket.texcoords(), ARRAY_BUFFER, FLOAT, "VEC2", vertexCount, false, null);
         int color = accessor(bin, bufferViews, accessors, bucket.colors(), ARRAY_BUFFER, UNSIGNED_BYTE, "VEC4", vertexCount, true, null);

         // uint16 indices while the bucket fits, which halves the index block on most exports.
         boolean shortIndices = vertexCount <= MAX_UNSIGNED_SHORT;
         int indices = accessor(
            bin,
            bufferViews,
            accessors,
            bucket.indices(shortIndices),
            ELEMENT_ARRAY_BUFFER,
            shortIndices ? UNSIGNED_SHORT : UNSIGNED_INT,
            "SCALAR",
            bucket.indexCount(),
            false,
            null
         );

         int material = materials.size();
         materials.add(material(entry.getKey()));
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

   private static String material(AlphaMode mode) {
      StringBuilder json = new StringBuilder();
      json.append("{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0},\"metallicFactor\":0,\"roughnessFactor\":1},");
      json.append("\"extensions\":{\"KHR_materials_unlit\":{}},");
      json.append("\"alphaMode\":\"").append(mode.name()).append('"');
      if (mode == AlphaMode.MASK) {
         json.append(",\"alphaCutoff\":0.5");
      }

      // Foliage and glass are two-sided in-game; single-sided faces would punch holes in the build.
      if (mode != AlphaMode.OPAQUE) {
         json.append(",\"doubleSided\":true");
      }

      return json.append(",\"name\":\"").append(mode.name().toLowerCase(Locale.ROOT)).append("\"}").toString();
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
      StringBuilder json = new StringBuilder();
      json.append("{\"bufferView\":").append(view).append(",\"componentType\":").append(componentType);
      json.append(",\"count\":").append(count).append(",\"type\":\"").append(type).append('"');
      if (normalized) {
         json.append(",\"normalized\":true");
      }

      if (minMax != null) {
         json.append(",\"min\":[").append(join(minMax[0])).append("],\"max\":[").append(join(minMax[1])).append(']');
      }

      accessors.add(json.append('}').toString());
      return accessors.size() - 1;
   }

   private static int bufferView(ByteArrayOutputStream bin, List<String> bufferViews, byte[] data, int target) {
      pad(bin, 4);
      int offset = bin.size();
      bin.writeBytes(data);
      String json = "{\"buffer\":0,\"byteOffset\":" + offset + ",\"byteLength\":" + data.length + (target > 0 ? ",\"target\":" + target : "") + "}";
      bufferViews.add(json);
      return bufferViews.size() - 1;
   }

   private static byte[] container(byte[] json, byte[] bin) {
      int jsonPad = (4 - json.length % 4) % 4;
      int binPad = (4 - bin.length % 4) % 4;
      int total = HEADER_BYTES + CHUNK_HEADER_BYTES + json.length + jsonPad + CHUNK_HEADER_BYTES + bin.length + binPad;
      ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
      buffer.putInt(GLB_MAGIC);
      buffer.putInt(GLB_VERSION);
      buffer.putInt(total);

      buffer.putInt(json.length + jsonPad);
      buffer.putInt(JSON_CHUNK);
      buffer.put(json);
      for (int i = 0; i < jsonPad; i++) {
         buffer.put(JSON_PADDING);
      }

      buffer.putInt(bin.length + binPad);
      buffer.putInt(BIN_CHUNK);
      buffer.put(bin);
      for (int i = 0; i < binPad; i++) {
         buffer.put(BIN_PADDING);
      }

      return buffer.array();
   }

   private static void pad(ByteArrayOutputStream out, int align) {
      for (int i = (align - out.size() % align) % align; i > 0; i--) {
         out.write(0);
      }
   }

   private static String join(float[] values) {
      StringBuilder joined = new StringBuilder();
      for (int i = 0; i < values.length; i++) {
         if (i > 0) {
            joined.append(',');
         }

         // Shortest text that parses back to the same float; rounding would break the accessor bounds.
         joined.append(values[i]);
      }

      return joined.toString();
   }

   // One alpha mode's worth of geometry: deduplicated vertices plus a triangle index list.
   private static final class Bucket {
      private final Map<Vertex, Integer> indexByVertex = new HashMap<>();
      private final List<Vertex> vertices = new ArrayList<>();
      private int[] indices = new int[1024];
      private int indexCount;
      private final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
      private final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

      int addVertex(float x, float y, float z, float u, float v, byte shade) {
         Vertex key = new Vertex(x, y, z, u, v, shade);
         Integer existing = this.indexByVertex.get(key);
         if (existing != null) {
            return existing;
         }

         int index = this.vertices.size();
         this.vertices.add(key);
         this.indexByVertex.put(key, index);
         this.min[0] = Math.min(this.min[0], x);
         this.min[1] = Math.min(this.min[1], y);
         this.min[2] = Math.min(this.min[2], z);
         this.max[0] = Math.max(this.max[0], x);
         this.max[1] = Math.max(this.max[1], y);
         this.max[2] = Math.max(this.max[2], z);
         return index;
      }

      void addTriangle(int a, int b, int c) {
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

      byte[] positions() {
         ByteBuffer buffer = ByteBuffer.allocate(this.vertices.size() * 3 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
         for (Vertex vertex : this.vertices) {
            buffer.putFloat(vertex.x()).putFloat(vertex.y()).putFloat(vertex.z());
         }

         return buffer.array();
      }

      byte[] texcoords() {
         ByteBuffer buffer = ByteBuffer.allocate(this.vertices.size() * 2 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
         for (Vertex vertex : this.vertices) {
            buffer.putFloat(vertex.u()).putFloat(vertex.v());
         }

         return buffer.array();
      }

      byte[] colors() {
         // Grey, so r=g=b; VEC4 because glTF wants attribute elements 4-byte aligned.
         ByteBuffer buffer = ByteBuffer.allocate(this.vertices.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
         for (Vertex vertex : this.vertices) {
            buffer.put(vertex.shade()).put(vertex.shade()).put(vertex.shade()).put((byte)255);
         }

         return buffer.array();
      }

      byte[] indices(boolean shortIndices) {
         ByteBuffer buffer = ByteBuffer.allocate(this.indexCount * (shortIndices ? Short.BYTES : Integer.BYTES)).order(ByteOrder.LITTLE_ENDIAN);
         for (int i = 0; i < this.indexCount; i++) {
            if (shortIndices) {
               buffer.putShort((short)this.indices[i]);
            } else {
               buffer.putInt(this.indices[i]);
            }
         }

         return buffer.array();
      }
   }

   private record Vertex(float x, float y, float z, float u, float v, byte shade) {
   }

   // The atlas PNG, where each texture key sits in it and how it blends; a key missing from a map is untransformed and opaque.
   public record Atlas(byte[] png, Map<String, UvTransform> uvs, Map<String, AlphaMode> alphaModes) {
   }

   // Run with gradlew glbCheck.
   public static void main(String[] args) throws IOException {
      // Linearised shade factors: 1.0 / 0.8 / 0.6 / 0.5 through the sRGB transfer function.
      check(shadeByte(new float[]{0.0F, 1.0F, 0.0F}) == (byte)255, "top face is full bright");
      check(shadeByte(new float[]{0.0F, 0.0F, 1.0F}) == (byte)154, "south face is 0.8 linearised");
      check(shadeByte(new float[]{-1.0F, 0.0F, 0.0F}) == (byte)81, "west face is 0.6 linearised");
      check(shadeByte(new float[]{0.0F, -1.0F, 0.0F}) == (byte)55, "bottom face is 0.5 linearised");

      float[][] positions = {{0.0F, 1.0F, 0.0F}, {0.0F, 1.0F, 1.0F}, {1.0F, 1.0F, 1.0F}, {1.0F, 1.0F, 0.0F}};
      float[][] uv = {{0.0F, 0.0F}, {0.0F, 1.0F}, {1.0F, 1.0F}, {1.0F, 0.0F}};
      float[] up = {0.0F, 1.0F, 0.0F};
      TextureKey stone = TextureKey.generated("stone", client -> null);
      TextureKey leaves = TextureKey.generated("leaves", client -> null);
      MeshBuilder mesh = new MeshBuilder();
      mesh.addQuad(stone, positions, uv, up);
      mesh.addQuad(stone, positions, uv, up);
      mesh.addQuad(leaves, positions, uv, up);

      Path dir = Files.createTempDirectory("meshify-check");
      Path file = dir.resolve("check.glb");
      write(
         file,
         mesh,
         new GlbWriter.Atlas(
            new byte[]{1, 2, 3},
            Map.of("stone", UvTransform.IDENTITY, "leaves", UvTransform.IDENTITY),
            Map.of("stone", AlphaMode.OPAQUE, "leaves", AlphaMode.MASK)
         )
      );

      byte[] glb = Files.readAllBytes(file);
      ByteBuffer reader = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
      check(reader.getInt() == GLB_MAGIC, "glTF magic");
      check(reader.getInt() == GLB_VERSION, "glTF version 2");
      check(reader.getInt() == glb.length, "header length matches the file");

      int jsonLength = reader.getInt();
      check(reader.getInt() == JSON_CHUNK, "first chunk is JSON");
      check(jsonLength % 4 == 0, "JSON chunk is 4-byte aligned");
      byte[] jsonBytes = new byte[jsonLength];
      reader.get(jsonBytes);
      String json = new String(jsonBytes, StandardCharsets.UTF_8).trim();

      int binLength = reader.getInt();
      check(reader.getInt() == BIN_CHUNK, "second chunk is BIN");
      check(binLength % 4 == 0, "BIN chunk is 4-byte aligned");
      check(HEADER_BYTES + CHUNK_HEADER_BYTES + jsonLength + CHUNK_HEADER_BYTES + binLength == glb.length, "chunks fill the file exactly");

      // Two identical quads must dedup to 4 vertices and 12 indices.
      check(json.contains("\"count\":4,\"type\":\"VEC3\""), "opaque quads deduplicated to 4 vertices");
      check(json.contains("\"count\":12,\"type\":\"SCALAR\""), "opaque bucket emitted 12 indices");
      check(json.contains("\"alphaMode\":\"MASK\",\"alphaCutoff\":0.5"), "cutout material");
      check(json.contains("\"alphaMode\":\"OPAQUE\""), "opaque material");
      check(!json.contains("BLEND"), "no empty blend bucket");
      check(json.contains("KHR_materials_unlit"), "unlit extension declared");
      check(json.contains("\"magFilter\":9728"), "nearest sampling");
      check(!json.contains("NORMAL"), "no normal attribute");
      check(Float.parseFloat(join(new float[]{0.9930997F})) == 0.9930997F, "accessor bounds keep full float precision");
      Matcher matcher = Pattern.compile("\"buffers\":\\[\\{\"byteLength\":(\\d+)").matcher(json);
      check(matcher.find(), "buffer declared");
      int declared = Integer.parseInt(matcher.group(1));
      // The BIN chunk is padded up to a 4-byte boundary, so it can exceed the buffer by 1-3 bytes.
      check(declared <= binLength && binLength - declared < 4, "declared buffer length matches the BIN chunk");

      Files.delete(file);
      Files.delete(dir);
      System.out.println("GlbWriter self-check passed");
   }

   private static void check(boolean passed, String description) {
      if (!passed) {
         throw new AssertionError("GlbWriter self-check failed: " + description);
      }
   }
}
