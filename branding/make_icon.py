"""Original pixel-art icon for Lively Villagers: a villager-style head with a speech bubble.
Drawn on a 32x32 grid, then scaled with nearest-neighbour. No game textures are used."""
import struct, zlib

N = 32
C = {
    '.': None,
    'g': (46, 139, 87), 'G': (34, 110, 68), 'k': (24, 74, 46),      # background greens + border
    's': (189, 140, 101), 'S': (160, 112, 78), 'd': (124, 84, 58),    # skin, shadow, deep shadow
    'h': (98, 66, 44), 'H': (74, 49, 33),                            # hair/hood
    'b': (52, 34, 22),                                                # brow / mouth
    'w': (250, 250, 245), 'e': (60, 170, 80),                         # eye white, green iris
    'n': (171, 120, 85), 'N': (139, 95, 66),                          # nose light / dark
    'W': (255, 255, 255), 'o': (40, 40, 40),                          # bubble fill / outline
    'r': (220, 50, 60), 'R': (170, 30, 40),                           # heart
    'c': (110, 82, 60), 'C': (86, 62, 44),                            # robe
}
grid = [['.'] * N for _ in range(N)]

def rect(x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < N and 0 <= y < N:
                grid[y][x] = ch

# Rounded-square background with a darker rim and a subtle lower shade.
for y in range(N):
    for x in range(N):
        corner = min(x, N - 1 - x) + min(y, N - 1 - y)
        if corner < 3:
            continue
        edge = x in (0, N - 1) or y in (0, N - 1) or corner == 3
        grid[y][x] = 'k' if edge else ('G' if y > 20 else 'g')

# Robe / shoulders at the bottom.
rect(4, 28, 25, 30, 'c'); rect(4, 30, 25, 30, 'C'); rect(13, 28, 16, 30, 'C')
# Head (tall, villager-like proportions).
rect(7, 9, 22, 27, 's')
rect(7, 9, 22, 12, 'h'); rect(7, 9, 22, 9, 'H')          # hair band
rect(7, 13, 7, 27, 'S'); rect(22, 13, 22, 27, 'S')        # side shading
rect(8, 26, 21, 27, 'S')                                   # chin shade
rect(9, 15, 20, 15, 'b')                                   # unibrow
rect(9, 17, 10, 18, 'w'); rect(11, 17, 11, 18, 'e')        # left eye
rect(18, 17, 19, 18, 'w'); rect(17, 17, 17, 18, 'e')       # right eye
rect(13, 16, 16, 24, 'n'); rect(13, 22, 16, 25, 'N')      # long nose
rect(16, 16, 16, 25, 'N')
rect(11, 26, 18, 26, 'b')                                  # mouth
rect(12, 25, 12, 25, 'd'); rect(17, 25, 17, 25, 'd')       # smile corners

# Speech bubble top-right with a heart.
rect(17, 2, 29, 9, 'W')
for x in range(17, 30):
    grid[1][x] = 'o'; grid[10][x] = 'o'
for y in range(2, 10):
    grid[y][16] = 'o'; grid[y][30] = 'o'
for (x, y) in ((16, 1), (30, 1), (16, 10), (30, 10)):
    grid[y][x] = '.' if grid[y][x] == 'o' else grid[y][x]
# Tail: a 1-pixel opening in the bottom edge that narrows to a point toward the head.
grid[10][20] = 'W'
grid[11][19] = 'o'; grid[11][20] = 'W'; grid[11][21] = 'o'
grid[12][19] = 'o'; grid[12][20] = 'o'
heart = [".rr.rr.", "rrrrrrr", "rrrrrrR", ".rrrrR.", "..rRR..", "...R..."]
for j, row in enumerate(heart):
    for i, ch in enumerate(row):
        if ch != '.':
            grid[3 + j][20 + i] = ch

def write_png(path, scale):
    size = N * scale
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            px = C[grid[y // scale][x // scale]]
            raw += bytes((*px, 255)) if px else b'\x00\x00\x00\x00'
    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    open(path, 'wb').write(png)

write_png('icon-512.png', 16)
write_png('icon-128.png', 4)
