#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <iostream>

using namespace std;


double * generate_perturbation_array(double * image, const int rows, const int cols, const int slices)
{
	float norm_G = 0.0;
    const int offset = rows * cols;
    const int size = offset * slices;
    double * G_x_h    = new double[size];
    double * G_y_h    = new double[size];
    double * G_norm_h = new double[size];
    double * G_h      = new double[size];
    double * v_h      = new double[size];
	// 1. Calculate the difference at each pixel with respect to rows and columns and get the normalization factor for this pixel
	for(int s = 0; s < slices; ++s )
	{
		for(int r = 0; r < rows - 1; ++r )
		{
			for(int c = 0; c < cols - 1; ++c)
			{
				const int voxel = c + r * cols + s * offset;
				G_x_h[voxel]	= image[voxel + 1] - image[voxel];
				G_y_h[voxel]	= image[voxel + cols] - image[voxel];
				G_norm_h[voxel] = sqrt( pow( G_x_h[voxel], 2 ) + pow( G_y_h[voxel], 2 ) );
			}
		}
	}

	// 2. Add the appropriate difference values to each pixel subgradient
	for(int s = 0; s < slices; ++s )
	{
		for(int r = 0; r < rows - 1; ++r )
		{
			for(int c = 0; c < cols - 1; ++c )
			{
				const int voxel = c + r * cols + s * offset;
				if( G_norm_h[voxel] > 0.0 )
				{
					G_h[voxel]        -= ( G_x_h[voxel] + G_y_h[voxel] ) / G_norm_h[voxel];		// Negative signs on x/y terms applied using -=
					G_h[voxel + cols] += G_y_h[voxel] / G_norm_h[voxel];
					G_h[voxel + 1]    += G_x_h[voxel] / G_norm_h[voxel];
				}
			}
		}
	}

	// 3. Get the norm of the subgradient vector
	for(int i = 0; i < size; ++i )
		norm_G += pow( G_h[i], 2 );
		//G_norm += G_h[voxel] * G_h[voxel];
	norm_G = sqrt(norm_G);

	// 4. Normalize the subgradient of the TV to produce v
	// If norm_G = 0, all elements of G_h are zero => all elements of v_h = 0.
	if( norm_G != 0 )
	{
		for(int i = 0; i < size; ++i )
			v_h[i] = G_h[i] / norm_G;			// Standard implementation where steepest descent is applied directly by inserting negative sign here
			//v_h[voxel] = -G_h[voxel] / norm_G;		// Negative sign applied as subtraction in application of perturbation, eliminating unnecessary op
	}
	else
	{
		for(int i = 0; i < size; ++i )
			v_h[i] = 0.0;
	}

    delete[] G_x_h;
    delete[] G_y_h;
    delete[] G_norm_h;
    delete[] G_h;

    return v_h;
}


double * readImage(const char * filename, int * rows, int * cols, int * slices)
{
    FILE * fp = fopen(filename, "rb");
    fread(rows,   sizeof(int), 1, fp);
    fread(cols,   sizeof(int), 1, fp);
    fread(slices, sizeof(int), 1, fp);
    cout << "Read : " << " rows = " << *rows
         << "; cols = " << *cols
         << "; slices = " << *slices << endl;
    int length = (*rows) * (*cols) * (*slices);
    double * im = new double[length];
    fread(im, sizeof(double), length, fp );
    fclose(fp);
    return im;
}


bool equals(const double * a, const double * b, const size_t size)
{
    for (int i = 0; i < size; ++i) {
        if (abs(a[i] - b[i]) > 0.000001) {
            printf("a[%d] = %.8f, b[%d] = %.8f\n", i, a[i], i, b[i]);
            return false;
        }
    }
    return true;
}

int main(int argc, char *argv[])
{
    int rows_in, cols_in, slices_in;
    int rows_out, cols_out, slices_out;
    double * input = readImage(argv[1], &rows_in, &cols_in, &slices_in);
    double * output = readImage(argv[2], &rows_out, &cols_out, &slices_out);
    if ((rows_in != rows_out) or (cols_in != cols_out) or (slices_in != slices_out)) {
        puts("Error: input and output images have different dimensions.");
        cout << "rows_in : "   << rows_in   << " <--> " << "rows_out: "   << rows_out   << endl;
        cout << "cols_in : "   << cols_in   << " <--> " << "cols_out: "   << cols_out   << endl;
        cout << "slices_in : " << slices_in << " <--> " << "slices_out: " << slices_out << endl;
        return 0;
    }
    double * tv_out = generate_perturbation_array(input, rows_in, cols_in, slices_in);
    if (equals(tv_out, output, rows_in * cols_in * slices_in)) {
        puts("good.");
    } else {
        puts("bad.");
    }
    return 0;
}
