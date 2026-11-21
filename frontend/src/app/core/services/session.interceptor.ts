import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const sentToken = request.headers.get('Authorization');
  return next(request).pipe(catchError((error: HttpErrorResponse) => {
    // A late response from an old session must not close a newly established session.
    if (error.status === 401 && request.url.startsWith('/api/') &&
        request.url !== '/api/auth/login' && sentToken &&
        sentToken === `Bearer ${auth.getToken()}`) auth.logout();
    return throwError(() => error);
  }));
};
