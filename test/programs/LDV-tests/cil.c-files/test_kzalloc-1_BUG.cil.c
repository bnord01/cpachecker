/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 211 "/usr/lib64/gcc/x86_64-suse-linux/4.5/include/stddef.h"
typedef unsigned long size_t;
#line 18 "./test_kzalloc-1_BUG.c"
struct A {
   int *a ;
   int *b ;
};
#line 69 "/usr/include/assert.h"
extern  __attribute__((__nothrow__, __noreturn__)) void __assert_fail(char const   *__assertion ,
                                                                      char const   *__file ,
                                                                      unsigned int __line ,
                                                                      char const   *__function ) ;
#line 473 "/usr/include/stdlib.h"
extern  __attribute__((__nothrow__)) void *calloc(size_t __nmemb , size_t __size )  __attribute__((__malloc__)) ;
#line 6 "./test_kzalloc-1_BUG.c"
int VERDICT_SAFE  ;
#line 7 "./test_kzalloc-1_BUG.c"
int CURRENTLY_UNSAFE  ;
#line 23 "./test_kzalloc-1_BUG.c"
int main(void) 
{ struct A *x ;
  void *tmp ;

  {
  {
#line 25
  tmp = calloc(0UL, sizeof(struct A ));
#line 25
  x = (struct A *)tmp;
  }
#line 32
  if ((unsigned long )x->a == (unsigned long )((int *)0)) {

  } else {
    {
#line 32
    __assert_fail("x->a == 0", "./test_kzalloc-1_BUG.c", 32U, "main");
    }
  }
#line 33
  return (0);
}
}
