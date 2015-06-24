// Secure program
int main(int h, int l, int b) {
  int x = 0;
  int sec = 0;
  if(b){x=h;sec = 1;}
  else {x = l;}
  if(!sec){return (x);}
  else return (0);
}