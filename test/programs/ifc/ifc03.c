typedef struct {int f;} foo;

int main(int h) {
  foo x;
  x.f = h;
  return x.f;
}