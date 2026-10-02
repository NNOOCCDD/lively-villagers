"""Original pixel-art icon for Lively Villagers (v2, cleaner): a front-facing villager-style head
with a small speech bubble. 32x32 grid, villager proportions at 2x (head 8x10 -> 16x20), two-tone
shading only, scaled with nearest-neighbour. No game textures are used."""
import struct, zlib

N = 32
P = {
    'o': (26, 74, 47),      # background outline
    'g': (52, 150, 95),     # background
    'G': (66, 168, 110),    # background top light
    'k': (40, 30, 22),      # dark outline (head/bubble)
    'h': (92, 62, 40),      # hair
    's': (206, 158, 120),   # skin
    'S': (180, 132, 98),    # skin shade
    'n': (186, 136, 100),   # nose
    'N': (150, 104, 74),    # nose shade
    'b': (64, 42, 28),      # brow
    'w': (245, 242, 232),   # eye white
    'e': (46, 160, 86),     # iris
    'r': (122, 86, 58),     # robe
    'R': (98, 68, 46),      # robe shade
    'W': (255, 253, 247),   # bubble
    'H': (226, 58, 68),     # heart
    'D': (178, 36, 48),     # heart shade
}
g = [[None] * N for _ in range(N)]

def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        g[y][x] = c

def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(x, y, c)

def rounded(x0, y0, x1, y1, r):
    """Pixels inside a rounded rectangle (corner radius r, in pixels)."""
    inside = set()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cx = min(max(x, x0 + r), x1 - r)
            cy = min(max(y, y0 + r), y1 - r)
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + r * 0.6:
                inside.add((x, y))
    return inside

def fill_shape(shape, fill, outline):
    for (x, y) in shape:
        edge = any((x + dx, y + dy) not in shape for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        put(x, y, outline if edge else fill(x, y))

# Background: rounded square, clean 1px outline, lighter upper band.
fill_shape(rounded(0, 0, N - 1, N - 1, 5), lambda x, y: 'G' if y < 15 else 'g', 'o')

# Robe (shoulders) under the head, clipped to the background's inner area.
bg = rounded(0, 0, N - 1, N - 1, 5)
for y in range(27, 31):
    for x in range(3, 25):
        if (x, y) in bg and g[y][x] != 'o':
            put(x, y, 'R' if y == 27 or 12 <= x <= 15 else 'r')

# Head 16x20 (villager 8x10 at 2x), 1px dark outline, two-tone skin.
X0, Y0, X1, Y1 = 6, 7, 21, 26
rect(X0 - 1, Y0 - 1, X1 + 1, Y1 + 1, 'k')
rect(X0, Y0, X1, Y1, 's')
rect(X1 - 1, Y0, X1, Y1, 'S')
rect(X0, Y1 - 1, X1, Y1, 'S')
rect(X0, Y0, X1, Y0 + 3, 'h')
rect(X0, Y0 + 4, X0, Y0 + 5, 'h'); rect(X1, Y0 + 4, X1, Y0 + 5, 'h')
# Brow, then eyes (white outer, green inner).
rect(X0 + 2, Y0 + 6, X1 - 2, Y0 + 7, 'b')
rect(X0 + 2, Y0 + 8, X0 + 3, Y0 + 9, 'w'); rect(X0 + 4, Y0 + 8, X0 + 5, Y0 + 9, 'e')
rect(X1 - 5, Y0 + 8, X1 - 4, Y0 + 9, 'e'); rect(X1 - 3, Y0 + 8, X1 - 2, Y0 + 9, 'w')
# Long nose, 4x8, shaded right and bottom.
NX = X0 + 6
rect(NX, Y0 + 9, NX + 3, Y0 + 16, 'n')
rect(NX + 3, Y0 + 9, NX + 3, Y0 + 16, 'N')
rect(NX, Y0 + 16, NX + 3, Y0 + 16, 'N')

# Speech bubble: rounded, clean outline, short tail angled down-left toward the head.
bubble = rounded(17, 1, 30, 10, 3)
fill_shape(bubble, lambda x, y: 'W', 'k')
put(22, 10, 'W'); put(23, 10, 'W')                 # opening in the bottom edge
put(21, 10, 'k'); put(24, 10, 'k')
put(22, 11, 'W'); put(21, 11, 'k'); put(23, 11, 'k')
put(21, 12, 'k'); put(22, 12, 'k')
heart = [".HH.HH.", "HHHHHHD", "HHHHHHD", ".HHHHD.", "..HHD..", "...D..."]
for j, row in enumerate(heart):
    for i, c in enumerate(row):
        if c != '.':
            put(20 + i, 3 + j, c)

def write_png(path, scale):
    size = N * scale
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            c = g[y // scale][x // scale]
            raw += bytes((*P[c], 255)) if c else b'\x00\x00\x00\x00'
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(png)

write_png('icon-512.png', 16)
write_png('icon-128.png', 4)
