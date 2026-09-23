import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ToastService } from './toast';

@Component({
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App { readonly toast = inject(ToastService); }
